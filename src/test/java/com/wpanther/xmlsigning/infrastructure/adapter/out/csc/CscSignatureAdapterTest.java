package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CscSignatureAdapterTest {

    @Mock
    private CSCSignatureClient feignClient;

    @InjectMocks
    private CscSignatureAdapter adapter;

    @Test
    void signHash_mapsCommandToFeignRequestAndReturnsResult() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"base64-signature-value"})
                .certificate("base64-cert")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "client-1", "cred-1", "sad-token-xyz",
                "SHA-256withRSA", List.of("digest-abc"),
                "XAdES", "XAdES-BASELINE-T", "enveloped",
                "SHA256", System.currentTimeMillis());

        CscSignHashResult result = adapter.signHash(cmd);

        assertThat(result.signatures()).containsExactly("base64-signature-value");
        assertThat(result.certificate()).isEqualTo("base64-cert");
    }

    @Test
    void signHash_mapsTopLevelFieldsCorrectly() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-1"})
                .certificate("cert-1")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        long signDate = 1234567890L;
        CscSignHashCommand cmd = new CscSignHashCommand(
                "my-client", "my-cred", "my-sad-token",
                "SHA-256withRSA", List.of("digest-1"),
                "XAdES", "XAdES-BASELINE-T", "enveloped",
                "SHA256", signDate);

        adapter.signHash(cmd);

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        CSCSignatureRequest captured = captor.getValue();

        assertThat(captured.getClientId()).isEqualTo("my-client");
        assertThat(captured.getCredentialID()).isEqualTo("my-cred");
        assertThat(captured.getSAD()).isEqualTo("my-sad-token");
        assertThat(captured.getHashAlgo()).isEqualTo("SHA-256withRSA");
    }

    @Test
    void signHash_mapsSignatureAttributesCorrectly() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-value"})
                .certificate("cert-value")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        long signDate = 9999999999L;
        CscSignHashCommand cmd = new CscSignHashCommand(
                "client-x", "cred-x", "sad-x",
                "SHA-384withRSA", List.of("hash-x"),
                "XAdES", "XAdES-BASELINE-LT", "enveloping",
                "SHA384", signDate);

        adapter.signHash(cmd);

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        CSCSignatureRequest captured = captor.getValue();

        assertThat(captured.getSignatureData()).isNotNull();
        assertThat(captured.getSignatureData().getHashToSign()).containsExactly("hash-x");
        assertThat(captured.getSignatureData().getSignatureAttributes()).isNotNull();
        assertThat(captured.getSignatureData().getSignatureAttributes().getSignatureType()).isEqualTo("XAdES");
        assertThat(captured.getSignatureData().getSignatureAttributes().getSignatureLevel()).isEqualTo("XAdES-BASELINE-LT");
        assertThat(captured.getSignatureData().getSignatureAttributes().getSignatureForm()).isEqualTo("enveloping");
        assertThat(captured.getSignatureData().getSignatureAttributes().getDigestAlgorithm()).isEqualTo("SHA384");
        assertThat(captured.getSignatureData().getSignatureAttributes().getSignDate()).isEqualTo(signDate);
    }

    @Test
    void signHash_convertsMultipleDocumentDigestsToArray() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-a", "sig-b"})
                .certificate("cert-multi")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "client-m", "cred-m", "sad-m",
                "SHA-256withRSA", List.of("digest-a", "digest-b"),
                "XAdES", "XAdES-BASELINE-T", "enveloped",
                "SHA256", System.currentTimeMillis());

        CscSignHashResult result = adapter.signHash(cmd);

        assertThat(result.signatures()).containsExactly("sig-a", "sig-b");

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        assertThat(captor.getValue().getSignatureData().getHashToSign())
                .containsExactly("digest-a", "digest-b");
    }

    @Test
    void signHash_propagatesCscSignatureExceptionWithoutDoubleWrapping() {
        CscSignatureException original = new CscSignatureException(
                "HSM unavailable", new RuntimeException("timeout"), "txn-99");
        when(feignClient.signHash(any())).thenThrow(original);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "client-1", "cred-1", "sad-1",
                "SHA-256withRSA", List.of("digest-1"),
                "XAdES", "XAdES-BASELINE-T", "enveloped",
                "SHA256", System.currentTimeMillis());

        Throwable thrown = catchThrowable(() -> adapter.signHash(cmd));
        assertThat(thrown).isSameAs(original);
    }

    @Test
    void signHash_wrapsGenericExceptionInCscSignatureException() {
        when(feignClient.signHash(any())).thenThrow(new RuntimeException("network error"));

        CscSignHashCommand cmd = new CscSignHashCommand(
                "client-1", "cred-1", "sad-1",
                "SHA-256withRSA", List.of("digest-1"),
                "XAdES", "XAdES-BASELINE-T", "enveloped",
                "SHA256", System.currentTimeMillis());

        assertThatThrownBy(() -> adapter.signHash(cmd))
                .isInstanceOf(CscSignatureException.class)
                .hasMessageContaining("network error");
    }
}
