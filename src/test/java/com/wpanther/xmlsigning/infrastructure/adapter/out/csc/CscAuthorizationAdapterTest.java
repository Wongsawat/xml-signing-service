package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;
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
    void authorize_mapsCommandToFeignRequestAndReturnsResult() {
        CSCAuthorizeResponse feignResponse = CSCAuthorizeResponse.builder()
            .SAD("sad-token-abc")
            .transactionID("txn-123")
            .build();
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "client-1", "cred-1", "1", "SHA256",
            List.of("digest1"), "Thai e-Tax signing");
        CscAuthorizeResult result = adapter.authorize(cmd);

        assertThat(result.sadToken()).isEqualTo("sad-token-abc");
        assertThat(result.transactionId()).isEqualTo("txn-123");

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        CSCAuthorizeRequest captured = captor.getValue();
        assertThat(captured.getClientId()).isEqualTo("client-1");
        assertThat(captured.getCredentialID()).isEqualTo("cred-1");
        assertThat(captured.getHashAlgo()).isEqualTo("SHA256");
    }

    @Test
    void authorize_mapsMultipleDigests() {
        CSCAuthorizeResponse feignResponse = CSCAuthorizeResponse.builder()
            .SAD("sad-multi")
            .transactionID("txn-multi")
            .build();
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "client-2", "cred-2", "2", "SHA384",
            List.of("digest-a", "digest-b"), "Multi-doc signing");
        adapter.authorize(cmd);

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        CSCAuthorizeRequest captured = captor.getValue();
        assertThat(captured.getHash()).containsExactly("digest-a", "digest-b");
        assertThat(captured.getNumSignatures()).isEqualTo("2");
        assertThat(captured.getDescription()).isEqualTo("Multi-doc signing");
        assertThat(captured.getHashAlgo()).isEqualTo("SHA384");
    }
}
