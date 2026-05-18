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
                .responseID("resp-001")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "cred-1", "sad-token-xyz",
                "2.16.840.1.101.3.4.2.1", List.of("digest-abc"));

        CscSignHashResult result = adapter.signHash(cmd);

        assertThat(result.signatures()).containsExactly("base64-signature-value");
        assertThat(result.responseId()).isEqualTo("resp-001");
    }

    @Test
    void signHash_mapsTopLevelFieldsCorrectly() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-1"})
                .responseID("resp-002")
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "my-cred", "my-sad-token",
                "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

        adapter.signHash(cmd);

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        CSCSignatureRequest captured = captor.getValue();

        assertThat(captured.getCredentialID()).isEqualTo("my-cred");
        assertThat(captured.getSAD()).isEqualTo("my-sad-token");
        assertThat(captured.getHashAlgorithmOID()).isEqualTo("2.16.840.1.101.3.4.2.1");
        assertThat(captured.getHashes()).containsExactly("digest-1");
    }

    @Test
    void signHash_hasNoSignatureDataWrapperAndFlatHashesAtRoot() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-value"})
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "cred-x", "sad-x",
                "2.16.840.1.101.3.4.2.1", List.of("hash-x"));

        adapter.signHash(cmd);

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        assertThat(captor.getValue().getHashes()).containsExactly("hash-x");
    }

    @Test
    void signHash_convertsMultipleDocumentDigestsToArray() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                .signatures(new String[]{"sig-a", "sig-b"})
                .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "cred-m", "sad-m",
                "2.16.840.1.101.3.4.2.1", List.of("digest-a", "digest-b"));

        CscSignHashResult result = adapter.signHash(cmd);

        assertThat(result.signatures()).containsExactly("sig-a", "sig-b");

        ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
        verify(feignClient).signHash(captor.capture());
        assertThat(captor.getValue().getHashes()).containsExactly("digest-a", "digest-b");
    }

    @Test
    void signHash_propagatesCscSignatureExceptionWithoutDoubleWrapping() {
        CscSignatureException original = new CscSignatureException(
                "HSM unavailable", new RuntimeException("timeout"), "txn-99");
        when(feignClient.signHash(any())).thenThrow(original);

        CscSignHashCommand cmd = new CscSignHashCommand(
                "cred-1", "sad-1",
                "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

        Throwable thrown = catchThrowable(() -> adapter.signHash(cmd));
        assertThat(thrown).isSameAs(original);
    }

    @Test
    void signHash_wrapsGenericExceptionInCscSignatureException() {
        when(feignClient.signHash(any())).thenThrow(new RuntimeException("network error"));

        CscSignHashCommand cmd = new CscSignHashCommand(
                "cred-1", "sad-1",
                "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

        assertThatThrownBy(() -> adapter.signHash(cmd))
                .isInstanceOf(CscSignatureException.class)
                .hasMessageContaining("network error");
    }
}
