package com.wpanther.xmlsigning.application.usecase;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscCredentialInfoCache;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningServiceImpl")
class XmlSigningServiceImplTest {

    @Mock private CscSignaturePort signaturePort;
    @Mock private CscAuthorizationPort authorizationPort;
    @Mock private XadesEmbeddingPort xadesEmbeddingPort;
    @Mock private CscCredentialInfoCache credentialInfoCache;

    @InjectMocks
    private XmlSigningServiceImpl signingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(signingService, "credentialId", "test-credential");
        ReflectionTestUtils.setField(signingService, "hashAlgorithmOid", "2.16.840.1.101.3.4.2.1");
        ReflectionTestUtils.setField(signingService, "digestAlgorithm", "SHA-256");
        ReflectionTestUtils.setField(signingService, "pin", "");
        lenient().when(credentialInfoCache.getCertificate()).thenReturn("cached-cert-base64");
    }

    @Nested
    @DisplayName("signXml() Method")
    class SignXmlMethod {

        @Test
        @DisplayName("Signs XML successfully with valid response")
        void signXml_happyPath_returnsSigningResult() throws Exception {
            CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token");
            CscSignHashResult signResult = new CscSignHashResult(
                List.of("base64-encoded-signature"), "resp-001");

            when(authorizationPort.authorize(any(CscAuthorizeCommand.class))).thenReturn(authResult);
            when(signaturePort.signHash(any(CscSignHashCommand.class))).thenReturn(signResult);
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed><ds:Signature>test</ds:Signature></signed>"
                    .getBytes(StandardCharsets.UTF_8));

            SigningResult result = signingService.signXml("<xml>test</xml>", "doc-1");

            assertThat(result).isNotNull();
            assertThat(result.signedXml()).contains("ds:Signature");
            assertThat(result.certificate()).isEqualTo("cached-cert-base64");
            assertThat(result.responseId()).isEqualTo("resp-001");
            verify(credentialInfoCache).getCertificate();
            verify(authorizationPort, never()).authorize(
                argThat(c -> c.credentialId() == null));
        }

        @Test
        @DisplayName("Cert comes from cache, not from signHash response")
        void signXml_certificateComesFromCache() throws Exception {
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "resp-cert-test"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            signingService.signXml("<xml/>", "doc-cert");

            verify(credentialInfoCache).getCertificate();
            ArgumentCaptor<String> certCaptor = ArgumentCaptor.forClass(String.class);
            verify(xadesEmbeddingPort).embedSignature(any(), any(), anyString(),
                certCaptor.capture(), anyString());
            assertThat(certCaptor.getValue()).isEqualTo("cached-cert-base64");
        }

        @Test
        @DisplayName("authorize request must have credentialId, hashAlgorithmOid, hashes — no clientId")
        void signXml_sendsCorrectAuthorizeCommand() throws Exception {
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "resp-auth-test"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            signingService.signXml("<xml>test</xml>", "doc-1");

            ArgumentCaptor<CscAuthorizeCommand> captor =
                ArgumentCaptor.forClass(CscAuthorizeCommand.class);
            verify(authorizationPort).authorize(captor.capture());
            CscAuthorizeCommand cmd = captor.getValue();

            assertThat(cmd.credentialId()).isEqualTo("test-credential");
            assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
            assertThat(cmd.hashes()).isNotEmpty();
            assertThat(cmd.pin()).isNullOrEmpty();
            assertThat(cmd.description()).isEqualTo("Thai e-Tax Invoice XML Signing");
        }

        @Test
        @DisplayName("authorize request includes pin when pin is configured")
        void signXml_includesPinInAuthorizeCommand() throws Exception {
            ReflectionTestUtils.setField(signingService, "pin", "secret-pin");
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "resp-pin-test"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            signingService.signXml("<xml/>", "doc-pin");

            ArgumentCaptor<CscAuthorizeCommand> captor =
                ArgumentCaptor.forClass(CscAuthorizeCommand.class);
            verify(authorizationPort).authorize(captor.capture());
            assertThat(captor.getValue().pin()).isEqualTo("secret-pin");
        }

        @Test
        @DisplayName("signHash command must have credentialId, sadToken, hashAlgorithmOid, hashes")
        void signXml_sendsCorrectSignHashCommand() throws Exception {
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("test-sad-xyz"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "resp-sign-test"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            signingService.signXml("<xml>test</xml>", "doc-1");

            ArgumentCaptor<CscSignHashCommand> captor =
                ArgumentCaptor.forClass(CscSignHashCommand.class);
            verify(signaturePort).signHash(captor.capture());
            CscSignHashCommand cmd = captor.getValue();

            assertThat(cmd.credentialId()).isEqualTo("test-credential");
            assertThat(cmd.sadToken()).isEqualTo("test-sad-xyz");
            assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
            assertThat(cmd.hashes()).isNotEmpty();
        }

        @Test
        @DisplayName("responseId in SigningResult comes from signHash response")
        void signXml_responseIdFromSignHash() throws Exception {
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "CSC-RESPONSE-ABC"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            SigningResult result = signingService.signXml("<xml/>", "doc-1");

            assertThat(result.responseId()).isEqualTo("CSC-RESPONSE-ABC");
        }

        @Test
        @DisplayName("Calculates correct document digest")
        void signXml_calculatesCorrectDigest() throws Exception {
            String xmlContent = "<xml>test</xml>";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xmlContent.getBytes(StandardCharsets.UTF_8));
            String expectedDigest = Base64.getEncoder().encodeToString(hash);

            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            when(signaturePort.signHash(any())).thenReturn(
                new CscSignHashResult(List.of("sig"), "resp-digest-test"));
            when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

            signingService.signXml(xmlContent, "doc-1");

            ArgumentCaptor<CscAuthorizeCommand> authCaptor =
                ArgumentCaptor.forClass(CscAuthorizeCommand.class);
            verify(authorizationPort).authorize(authCaptor.capture());
            assertThat(authCaptor.getValue().hashes().get(0)).isEqualTo(expectedDigest);
        }

        @Test
        @DisplayName("Throws CscAuthorizationException when authorization port throws")
        void signXml_authorizationFails_throwsCscAuthorizationException() {
            CscAuthorizationException authException = new CscAuthorizationException(
                    "Authorization denied", null, null, "test-credential");
            when(authorizationPort.authorize(any())).thenThrow(authException);

            assertThatThrownBy(() -> signingService.signXml("<xml>test</xml>", "doc-auth-fail"))
                    .isInstanceOf(CscAuthorizationException.class)
                    .hasMessageContaining("Authorization denied");
        }

        @Test
        @DisplayName("Throws CscSignatureException when signature port throws")
        void signXml_signingFails_throwsCscSignatureException() {
            when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
            CscSignatureException signException = new CscSignatureException("HSM unavailable", (String) null);
            when(signaturePort.signHash(any())).thenThrow(signException);

            assertThatThrownBy(() -> signingService.signXml("<xml>test</xml>", "doc-sign-fail"))
                    .isInstanceOf(CscSignatureException.class)
                    .hasMessageContaining("HSM unavailable");
        }

        @Test
        @DisplayName("Wraps generic authorization exception in CscAuthorizationException")
        void signXml_genericAuthException_wrappedAsCscAuthorizationException() {
            when(authorizationPort.authorize(any())).thenThrow(new RuntimeException("Authorize failed"));

            assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                    .isInstanceOf(CscAuthorizationException.class)
                    .hasMessageContaining("CSC authorization failed");
        }
    }
}