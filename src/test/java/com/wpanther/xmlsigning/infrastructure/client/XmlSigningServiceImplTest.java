package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XmlSigningServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningServiceImpl")
class XmlSigningServiceImplTest {

    @Mock
    private CSCAuthClient authClient;

    @Mock
    private CSCApiClient apiClient;

    @InjectMocks
    private XmlSigningServiceImpl signingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(signingService, "clientId", "test-client");
        ReflectionTestUtils.setField(signingService, "credentialId", "test-credential");
        ReflectionTestUtils.setField(signingService, "hashAlgorithm", "SHA256");
        ReflectionTestUtils.setField(signingService, "signatureLevel", "XAdES-BASELINE-T");
        ReflectionTestUtils.setField(signingService, "digestAlgorithm", "SHA256");
    }

    @Nested
    @DisplayName("signXml() Method")
    class SignXmlMethod {

        @Test
        @DisplayName("Signs XML successfully with valid response")
        void testSignXmlSuccess() {
            // Setup auth response
            CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
                    .SAD("sad-token-123")
                    .transactionID("txn-auth-1")
                    .expiresIn(300L)
                    .build();
            when(authClient.authorize(any())).thenReturn(authResponse);

            // Setup sign response
            String signedXml = "<signed><xml>content</xml></signed>";
            String signedBase64 = Base64.getEncoder().encodeToString(
                    signedXml.getBytes(StandardCharsets.UTF_8));
            CSCSignDocumentResponse signResponse = CSCSignDocumentResponse.builder()
                    .signedDocument(signedBase64)
                    .transactionID("txn-sign-1")
                    .build();
            when(apiClient.signDocument(any())).thenReturn(signResponse);

            // Execute
            String result = signingService.signXml("<xml>test</xml>", "doc-1");

            // Verify
            assertThat(result).isEqualTo(signedXml);
        }

        @Test
        @DisplayName("Sends correct authorization request")
        void testAuthorizeRequestFields() {
            // Setup
            CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
                    .SAD("sad-token")
                    .build();
            when(authClient.authorize(any())).thenReturn(authResponse);
            when(apiClient.signDocument(any())).thenReturn(
                    CSCSignDocumentResponse.builder()
                            .signedDocument(Base64.getEncoder().encodeToString("<signed/>".getBytes()))
                            .build());

            // Execute
            signingService.signXml("<xml/>", "doc-1");

            // Capture and verify auth request
            ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
            verify(authClient).authorize(captor.capture());

            CSCAuthorizeRequest request = captor.getValue();
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("test-credential");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getValidityPeriod()).isEqualTo(300L);
            assertThat(request.getDescription()).isEqualTo("Thai e-Tax Invoice XML Signing");
        }

        @Test
        @DisplayName("Sends correct sign document request")
        void testSignDocumentRequestFields() throws Exception {
            // Setup
            String xmlContent = "<xml>test</xml>";
            String documentId = "doc-1";

            CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
                    .SAD("sad-token")
                    .build();
            when(authClient.authorize(any())).thenReturn(authResponse);

            String signedBase64 = Base64.getEncoder().encodeToString("<signed/>".getBytes());
            when(apiClient.signDocument(any())).thenReturn(
                    CSCSignDocumentResponse.builder()
                            .signedDocument(signedBase64)
                            .build());

            // Execute
            signingService.signXml(xmlContent, documentId);

            // Capture and verify sign request
            ArgumentCaptor<CSCSignDocumentRequest> captor = ArgumentCaptor.forClass(CSCSignDocumentRequest.class);
            verify(apiClient).signDocument(captor.capture());

            CSCSignDocumentRequest request = captor.getValue();
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("test-credential");
            assertThat(request.getSAD()).isEqualTo("sad-token");
            assertThat(request.getDocumentID()).isEqualTo(documentId);
            assertThat(request.getDocument()).isEqualTo(
                    Base64.getEncoder().encodeToString(xmlContent.getBytes(StandardCharsets.UTF_8)));
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureAttributes()).isNotNull();
            assertThat(request.getSignatureAttributes().getSignatureType()).isEqualTo("XAdES");
            assertThat(request.getSignatureAttributes().getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(request.getSignatureAttributes().getSignatureForm()).isEqualTo("enveloped");
            assertThat(request.getSignatureAttributes().getDigestAlgorithm()).isEqualTo("SHA256");
        }

        @Test
        @DisplayName("Calculates correct document digest")
        void testDigestCalculation() throws Exception {
            // Setup
            String xmlContent = "<xml>test</xml>";

            // Calculate expected digest
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            byte[] hash = digest.digest(xmlContent.getBytes(StandardCharsets.UTF_8));
            String expectedDigest = Base64.getEncoder().encodeToString(hash);

            CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
                    .SAD("sad-token")
                    .build();
            when(authClient.authorize(any())).thenReturn(authResponse);
            when(apiClient.signDocument(any())).thenReturn(
                    CSCSignDocumentResponse.builder()
                            .signedDocument(Base64.getEncoder().encodeToString("<signed/>".getBytes()))
                            .build());

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Verify digest
            ArgumentCaptor<CSCSignDocumentRequest> captor = ArgumentCaptor.forClass(CSCSignDocumentRequest.class);
            verify(apiClient).signDocument(captor.capture());
            assertThat(captor.getValue().getDocumentDigest()).isEqualTo(expectedDigest);
        }

        @Test
        @DisplayName("Throws RuntimeException when authorization fails")
        void testAuthorizationFailure() {
            // Setup
            when(authClient.authorize(any())).thenThrow(new RuntimeException("Auth failed"));

            // Execute & Verify
            assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("XML signing failed");
        }

        @Test
        @DisplayName("Throws RuntimeException when signing fails")
        void testSigningFailure() {
            // Setup
            CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
                    .SAD("sad-token")
                    .build();
            when(authClient.authorize(any())).thenReturn(authResponse);
            when(apiClient.signDocument(any())).thenThrow(new RuntimeException("Sign failed"));

            // Execute & Verify
            assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("XML signing failed");
        }
    }
}
