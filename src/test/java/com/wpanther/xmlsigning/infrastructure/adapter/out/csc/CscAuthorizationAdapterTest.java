package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
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
class CscAuthorizationAdapterTest {

    @Mock
    private CSCAuthClient feignClient;

    @InjectMocks
    private CscAuthorizationAdapter adapter;

    @Test
    void authorize_mapsCommandToFeignRequestAndReturnsSadToken() {
        CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
        feignResponse.setSAD("sad-token-abc");
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("digest1"), null, "Thai e-Tax signing");
        CscAuthorizeResult result = adapter.authorize(cmd);

        assertThat(result.sadToken()).isEqualTo("sad-token-abc");

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        CSCAuthorizeRequest captured = captor.getValue();
        assertThat(captured.getCredentialID()).isEqualTo("cred-1");
        assertThat(captured.getHashAlgorithmOID()).isEqualTo("2.16.840.1.101.3.4.2.1");
        assertThat(captured.getHashes()).containsExactly("digest1");
        assertThat(captured.getNumSignatures()).isEqualTo(1);
        assertThat(captured.getAuthData()).isNull();
    }

    @Test
    void authorize_includesAuthDataWhenPinNonBlank() {
        CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
        feignResponse.setSAD("sad-with-pin");
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("digest1"), "1234", "signing");
        adapter.authorize(cmd);

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        assertThat(captor.getValue().getAuthData()).isNotNull().hasSize(1);
        assertThat(captor.getValue().getAuthData().get(0).getId()).isEqualTo("PIN");
        assertThat(captor.getValue().getAuthData().get(0).getValue()).isEqualTo("1234");
    }

    @Test
    void authorize_omitsAuthDataWhenPinBlank() {
        CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
        feignResponse.setSAD("sad-no-pin");
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("digest1"), "", "signing");
        adapter.authorize(cmd);

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        assertThat(captor.getValue().getAuthData()).isNull();
    }

    @Test
    void authorize_propagatesExceptionWhenClientThrows() {
        when(feignClient.authorize(any())).thenThrow(new RuntimeException("CSC unavailable"));

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("digest1"), null, "description");

        assertThatThrownBy(() -> adapter.authorize(cmd))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("CSC unavailable");
    }

    @Test
    void authorize_mapsMultipleDigests() {
        CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
        feignResponse.setSAD("sad-multi");
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "cred-2", "2.16.840.1.101.3.4.2.1",
            List.of("digest-a", "digest-b"), null, "Multi-doc signing");
        adapter.authorize(cmd);

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        assertThat(captor.getValue().getHashes()).containsExactly("digest-a", "digest-b");
        assertThat(captor.getValue().getNumSignatures()).isEqualTo(1);
        assertThat(captor.getValue().getDescription()).isEqualTo("Multi-doc signing");
    }
}