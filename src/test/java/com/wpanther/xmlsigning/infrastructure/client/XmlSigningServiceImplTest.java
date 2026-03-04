package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashResult;
import com.wpanther.xmlsigning.domain.port.CscAuthorizationPort;
import com.wpanther.xmlsigning.domain.port.CscSignaturePort;
import com.wpanther.xmlsigning.domain.service.SigningResult;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XmlSigningServiceImpl}.
 * Tests the signHash pattern with SAD token authentication.
 *
 * <p>Both {@code authorizationPort} and {@code signaturePort} are mocked as domain port
 * interfaces. Adapter-level mapping (domain types → Feign DTOs) is tested separately in
 * {@code CscAuthorizationAdapterTest} and {@code CscSignatureAdapterTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningServiceImpl")
class XmlSigningServiceImplTest {

    @Mock
    private CscSignaturePort signaturePort;

    @Mock
    private CscAuthorizationPort authorizationPort;

    @Mock
    private XadesSignatureEmbedder signatureEmbedder;

    @InjectMocks
    private XmlSigningServiceImpl signingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(signingService, "clientId", "test-client");
        ReflectionTestUtils.setField(signingService, "credentialId", "test-credential");
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
            // Setup auth result (domain type)
            CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token", "txn-123");

            // Setup signHash result (domain type)
            String rawSignature = "base64-encoded-signature";
            String certificate = "base64-encoded-certificate";
            CscSignHashResult signResult = new CscSignHashResult(List.of(rawSignature), certificate);

            when(authorizationPort.authorize(any())).thenReturn(authResult);
            when(signaturePort.signHash(any())).thenReturn(signResult);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed><ds:Signature>test</ds:Signature></signed>");

            // Execute
            String originalXml = "<xml>test</xml>";
            SigningResult result = signingService.signXml(originalXml, "doc-1");

            // Verify result is not null and contains signature
            assertThat(result).isNotNull();
            assertThat(result.signedXml()).contains("ds:Signature");
            assertThat(result.certificate()).isEqualTo("base64-encoded-certificate");
            assertThat(result.transactionId()).isEqualTo("txn-123");
        }

        @Test
        @DisplayName("Sends correct CscSignHashCommand with SAD token (not PIN)")
        void testSignHashRequestFields() {
            // Setup
            String xmlContent = "<xml>test</xml>";

            CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token-xyz", "txn-456");
            CscSignHashResult signResult = new CscSignHashResult(List.of("signature-value"), "certificate-value");

            when(authorizationPort.authorize(any())).thenReturn(authResult);
            when(signaturePort.signHash(any())).thenReturn(signResult);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Verify authorize was called
            verify(authorizationPort).authorize(any());

            // Capture and verify CscSignHashCommand fields
            ArgumentCaptor<CscSignHashCommand> captor = ArgumentCaptor.forClass(CscSignHashCommand.class);
            verify(signaturePort).signHash(captor.capture());

            CscSignHashCommand command = captor.getValue();
            assertThat(command.clientId()).isEqualTo("test-client");
            assertThat(command.credentialId()).isEqualTo("test-credential");
            assertThat(command.hashAlgorithm()).isEqualTo("SHA256");

            // Verify SAD token is set
            assertThat(command.sadToken()).isEqualTo("test-sad-token-xyz");

            // Verify document digests contain hash
            assertThat(command.documentDigests()).isNotEmpty();
            assertThat(command.documentDigests().get(0)).isNotEmpty();

            // Verify signature attributes
            assertThat(command.signatureType()).isEqualTo("XAdES");
            assertThat(command.signatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(command.signatureForm()).isEqualTo("enveloped");
            assertThat(command.digestAlgorithm()).isEqualTo("SHA256");
        }

        @Test
        @DisplayName("Sends correct authorize command to port")
        void testAuthorizeCommand() {
            // Setup
            String xmlContent = "<xml>test</xml>";

            CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token", "txn-789");
            CscSignHashResult signResult = new CscSignHashResult(List.of("sig"), "cert");

            when(authorizationPort.authorize(any())).thenReturn(authResult);
            when(signaturePort.signHash(any())).thenReturn(signResult);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Capture and verify authorize command
            ArgumentCaptor<CscAuthorizeCommand> authCaptor =
                ArgumentCaptor.forClass(CscAuthorizeCommand.class);
            verify(authorizationPort).authorize(authCaptor.capture());

            var authCommand = authCaptor.getValue();
            assertThat(authCommand.clientId()).isEqualTo("test-client");
            assertThat(authCommand.credentialId()).isEqualTo("test-credential");
            assertThat(authCommand.numSignatures()).isEqualTo("1");
            assertThat(authCommand.hashAlgorithm()).isEqualTo("SHA256");
            assertThat(authCommand.description()).isEqualTo("Thai e-Tax Invoice XML Signing");
            assertThat(authCommand.documentDigests()).isNotNull();
            assertThat(authCommand.documentDigests()).hasSize(1);
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

            CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token", "txn-digest-test");
            CscSignHashResult signResult = new CscSignHashResult(List.of("sig"), "cert");

            when(authorizationPort.authorize(any())).thenReturn(authResult);
            when(signaturePort.signHash(any())).thenReturn(signResult);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml(xmlContent, "doc-1");

            // Verify digest in authorize command
            ArgumentCaptor<CscAuthorizeCommand> authCaptor =
                ArgumentCaptor.forClass(CscAuthorizeCommand.class);
            verify(authorizationPort).authorize(authCaptor.capture());
            assertThat(authCaptor.getValue().documentDigests().get(0)).isEqualTo(expectedDigest);

            // Verify digest in CscSignHashCommand
            ArgumentCaptor<CscSignHashCommand> signCaptor = ArgumentCaptor.forClass(CscSignHashCommand.class);
            verify(signaturePort).signHash(signCaptor.capture());
            assertThat(signCaptor.getValue().documentDigests().get(0)).isEqualTo(expectedDigest);
        }

        @Test
        @DisplayName("Throws CscAuthorizationException when signing fails")
        void testSigningFailure() {
            // Setup
            when(authorizationPort.authorize(any())).thenThrow(new RuntimeException("Authorize failed"));

            // Execute & Verify
            assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                    .isInstanceOf(com.wpanther.xmlsigning.domain.exception.CscAuthorizationException.class)
                    .hasMessageContaining("CSC authorization failed");
        }

        @Test
        @DisplayName("Uses SAD token from authorize result in CscSignHashCommand")
        void testSadTokenUsage() {
            // Setup
            String expectedSadToken = "received-sad-token-12345";

            CscAuthorizeResult authResult = new CscAuthorizeResult(expectedSadToken, "txn-001");
            CscSignHashResult signResult = new CscSignHashResult(List.of("sig"), "cert");

            when(authorizationPort.authorize(any())).thenReturn(authResult);
            when(signaturePort.signHash(any())).thenReturn(signResult);
            when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
                .thenReturn("<signed>xml</signed>");

            // Execute
            signingService.signXml("<xml/>", "doc-1");

            // Verify SAD token is used in CscSignHashCommand
            ArgumentCaptor<CscSignHashCommand> captor = ArgumentCaptor.forClass(CscSignHashCommand.class);
            verify(signaturePort).signHash(captor.capture());
            assertThat(captor.getValue().sadToken()).isEqualTo(expectedSadToken);
        }
    }
}
