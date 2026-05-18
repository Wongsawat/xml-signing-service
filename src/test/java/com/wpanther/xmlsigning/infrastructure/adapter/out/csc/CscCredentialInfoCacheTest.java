package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.CSCCredentialsInfoClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CscCredentialInfoCache Tests")
class CscCredentialInfoCacheTest {

    @Mock
    private CSCCredentialsInfoClient mockClient;

    private CscCredentialInfoCache cache;

    @BeforeEach
    void setUp() {
        cache = new CscCredentialInfoCache(mockClient);
        ReflectionTestUtils.setField(cache, "credentialId", "test-cred-id");
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshMethod {

        @Test
        @DisplayName("Should call credentials/info with configured credentialID and cache first cert")
        void shouldFetchAndCacheFirstCert() {
            when(mockClient.getCredentialInfo(any())).thenReturn(buildResponse("cert-base64"));

            cache.refresh();

            ArgumentCaptor<CSCCredentialsInfoRequest> captor =
                ArgumentCaptor.forClass(CSCCredentialsInfoRequest.class);
            verify(mockClient).getCredentialInfo(captor.capture());
            assertThat(captor.getValue().getCredentialID()).isEqualTo("test-cred-id");
            assertThat(cache.getCertificate()).isEqualTo("cert-base64");
        }

        @Test
        @DisplayName("getCertificate() returns cached value without additional client calls")
        void shouldNotCallClientOnSubsequentGet() {
            when(mockClient.getCredentialInfo(any())).thenReturn(buildResponse("cert"));

            cache.refresh();
            cache.getCertificate();
            cache.getCertificate();

            verify(mockClient, times(1)).getCredentialInfo(any());
        }

        @Test
        @DisplayName("refresh() again replaces the cached certificate")
        void shouldUpdateCacheOnSecondRefresh() {
            when(mockClient.getCredentialInfo(any()))
                .thenReturn(buildResponse("cert-first"))
                .thenReturn(buildResponse("cert-second"));

            cache.refresh();
            assertThat(cache.getCertificate()).isEqualTo("cert-first");

            cache.refresh();
            assertThat(cache.getCertificate()).isEqualTo("cert-second");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when certificate array is empty")
        void shouldThrowForEmptyCertArray() {
            CSCCredentialsInfoResponse.CertInfo certInfo =
                new CSCCredentialsInfoResponse.CertInfo(new String[0]);
            when(mockClient.getCredentialInfo(any()))
                .thenReturn(new CSCCredentialsInfoResponse(certInfo));

            assertThatThrownBy(() -> cache.refresh())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Should propagate exception when client throws")
        void shouldPropagateClientException() {
            when(mockClient.getCredentialInfo(any()))
                .thenThrow(new RuntimeException("CSC unavailable"));

            assertThatThrownBy(() -> cache.refresh())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSC unavailable");
        }
    }

    private CSCCredentialsInfoResponse buildResponse(String base64DerCert) {
        CSCCredentialsInfoResponse.CertInfo certInfo =
            new CSCCredentialsInfoResponse.CertInfo(new String[]{base64DerCert});
        return new CSCCredentialsInfoResponse(certInfo);
    }
}