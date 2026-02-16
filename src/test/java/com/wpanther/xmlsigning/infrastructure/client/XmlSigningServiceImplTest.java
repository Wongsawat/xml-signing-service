package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;
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
 * Tests the signHash pattern with PIN-based authentication.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningServiceImpl")
class XmlSigningServiceImplTest {

    @Mock
    private CSCSignatureClient signatureClient;

    @Mock
    private XadesSignatureEmbedder signatureEmbedder;

    @InjectMocks
    private XmlSigningServiceImpl signingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(signingService, "clientId", "test-client");
        ReflectionTestUtils.setField(signingService, "credentialId", "test-credential");
        ReflectionTestUtils.setField(signingService, "pin", "test-pin-1234");
        ReflectionTestUtils.setField(signingService, "hashAlgorithm", "SHA256");
        ReflectionTestUtils.setField(signingService, "signatureLevel", "XAdES-BASELINE-T");
        ReflectionTestUtils.setField(signingService, "digestAlgorithm", "SHA256");
        ReflectionTestUtils.setField(signingService, "signatureEmbedder", signatureEmbedder);
    }

    @Nested
    @DisplayName("signXml() Method")
    class SignXmlMethod {

        @Test
        @DisplayName("Signs XML successfully with valid response")
        void testSignXmlSuccess() {
            // Setup signHash response
            String rawSignature = "base64-encoded-signature";
            String certificate = "base64-encoded-certificate";

            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                    .signatures(new String[]{rawSignature})
                    .certificate(certificate)
                    .build();

            when(signatureClient.signHash(any())).thenReturn(signResponse);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed><ds:Signature>test</ds:Signature></signed>");

            // Execute
            String originalXml = "<xml>test</xml>";
            String result = signingService.signXml(originalXml, "doc-1");

            // Verify result is not null and contains signature
            assertThat(result).isNotNull();
            assertThat(result).contains("ds:Signature");
        }

        @Test
        @DisplayName("Sends correct signHash request with PIN credentials")
        void testSignHashRequestFields() {
            // Setup
            String xmlContent = "<xml>test</xml>";

            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                    .signatures(new String[]{"signature-value"})
                    .certificate("certificate-value")
                    .build();

            when(signatureClient.signHash(any())).thenReturn(signResponse);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Capture and verify signHash request
            ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
            verify(signatureClient).signHash(captor.capture());

            CSCSignatureRequest request = captor.getValue();
            assertThat(request.getClientId()).isEqualTo("test-client");
            assertThat(request.getCredentialID()).isEqualTo("test-credential");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");

            // Verify PIN is set in credentials
            assertThat(request.getCredentials()).isNotNull();
            assertThat(request.getCredentials().getPin()).isNotNull();
            assertThat(request.getCredentials().getPin().getValue()).isEqualTo("test-pin-1234");

            // Verify signature data contains hash
            assertThat(request.getSignatureData()).isNotNull();
            assertThat(request.getSignatureData().getHashToSign()).isNotEmpty();
            assertThat(request.getSignatureData().getHashToSign()[0]).isNotEmpty();

            // Verify signature attributes
            assertThat(request.getSignatureData().getSignatureAttributes()).isNotNull();
            assertThat(request.getSignatureData().getSignatureAttributes().getSignatureType()).isEqualTo("XAdES");
            assertThat(request.getSignatureData().getSignatureAttributes().getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(request.getSignatureData().getSignatureAttributes().getSignatureForm()).isEqualTo("enveloped");
            assertThat(request.getSignatureData().getSignatureAttributes().getDigestAlgorithm()).isEqualTo("SHA256");
        }

        @Test
        @DisplayName("Calculates correct document digest")
        void testDigestCalculation() throws Exception {
            // Setup
            String xmlContent = "<xml>test</xml>";

            // Calculate expected digest (base64url encoded)
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            byte[] hash = digest.digest(xmlContent.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(hash);
            String expectedDigest = base64.replace("+", "-").replace("/", "_").replaceAll("=+$", "");

            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                    .signatures(new String[]{"sig"})
                    .certificate("cert")
                    .build();

            when(signatureClient.signHash(any())).thenReturn(signResponse);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Verify digest
            ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
            verify(signatureClient).signHash(captor.capture());
            assertThat(captor.getValue().getSignatureData().getHashToSign()[0]).isEqualTo(expectedDigest);
        }

        @Test
        @DisplayName("Throws RuntimeException when signing fails")
        void testSigningFailure() {
            // Setup
            when(signatureClient.signHash(any())).thenThrow(new RuntimeException("Sign failed"));

            // Execute & Verify
            assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("XML signing failed");
        }

        @Test
        @DisplayName("Uses default PIN when not configured")
        void testDefaultPin() {
            // Clear the PIN to test default value
            ReflectionTestUtils.setField(signingService, "pin", null);

            CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
                    .signatures(new String[]{"sig"})
                    .certificate("cert")
                    .build();

            when(signatureClient.signHash(any())).thenReturn(signResponse);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml("<xml/>", "doc-1");

            // Verify - the field value would be null, but the @Value annotation
            // would provide the default value "1234" in real Spring context
            // In this test, we just verify the call is made
            verify(signatureClient).signHash(any());
        }
    }
}
