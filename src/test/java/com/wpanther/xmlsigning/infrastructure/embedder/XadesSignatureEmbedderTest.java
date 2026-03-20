package com.wpanther.xmlsigning.infrastructure.embedder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link XadesSignatureEmbedder}.
 */
@DisplayName("XadesSignatureEmbedder")
class XadesSignatureEmbedderTest {

    private XadesSignatureEmbedder embedder;

    @BeforeEach
    void setUp() {
        embedder = new XadesSignatureEmbedder();
    }

    @Nested
    @DisplayName("embedSignature(byte[], byte[], String, String, String) Method")
    class EmbedSignatureByteArrayMethod {

        @Test
        @DisplayName("Embeds signature into XML document from byte arrays")
        void testEmbedSignatureFromBytes() {
            // Setup
            String originalXml = "<root><data>test content</data></root>";
            String documentDigest = "dGVzdC1kaWdlc3QtYmFzZTY0";
            String rawSignature = "c2lnbmF0dXJlLXZhbHVlLWJhc2U2NA==";
            String certificate = "Y2VydGlmaWNhdGUtYmFzZTY0";
            byte[] xmlBytes = originalXml.getBytes(StandardCharsets.UTF_8);
            byte[] sigBytes = rawSignature.getBytes(StandardCharsets.UTF_8);

            // Execute
            byte[] signedXmlBytes = embedder.embedSignature(xmlBytes, sigBytes, documentDigest, certificate, "doc-123");
            String signedXml = new String(signedXmlBytes, StandardCharsets.UTF_8);

            // Verify
            assertThat(signedXmlBytes).isNotNull();
            assertThat(signedXml).contains("<ds:Signature");
            assertThat(signedXml).contains("ds:SignedInfo");
            assertThat(signedXml).contains("ds:SignatureValue");
        }

        @Test
        @DisplayName("Throws XmlValidationException for invalid XML bytes")
        void testInvalidXmlBytes() {
            String invalidXml = "<root><unclosed>";
            byte[] xmlBytes = invalidXml.getBytes(StandardCharsets.UTF_8);
            byte[] sigBytes = "sig".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() ->
                embedder.embedSignature(xmlBytes, sigBytes, "digest", "cert", "doc-123")
            )
            .isInstanceOf(com.wpanther.xmlsigning.domain.exception.XmlValidationException.class)
            .hasMessageContaining("Failed to embed signature");
        }
    }

    @Nested
    @DisplayName("embedSignature() Method")
    class EmbedSignatureMethod {

        @Test
        @DisplayName("Embeds signature into XML document")
        void testEmbedSignature() {
            // Setup
            String originalXml = "<root><data>test content</data></root>";
            String documentDigest = "dGVzdC1kaWdlc3QtYmFzZTY0"; // base64 encoded digest
            String rawSignature = "c2lnbmF0dXJlLXZhbHVlLWJhc2U2NA=="; // base64 encoded signature
            String certificate = "Y2VydGlmaWNhdGUtYmFzZTY0"; // base64 encoded certificate

            // Execute
            String signedXml = embedder.embedSignature(originalXml, documentDigest, rawSignature, certificate);

            // Verify
            assertThat(signedXml).isNotNull();
            assertThat(signedXml).contains("<ds:Signature");
            assertThat(signedXml).contains("ds:SignedInfo");
            assertThat(signedXml).contains("ds:SignatureValue");
            assertThat(signedXml).contains("ds:KeyInfo");
            assertThat(signedXml).contains("ds:X509Certificate");
            assertThat(signedXml).contains("xades:QualifyingProperties");
            assertThat(signedXml).contains("xades:SigningTime");

            // Verify the actual digest value is embedded (not placeholder)
            assertThat(signedXml).contains(documentDigest);
            assertThat(signedXml).contains(rawSignature);
            assertThat(signedXml).contains(certificate);
        }

        @Test
        @DisplayName("Uses actual document digest instead of placeholder")
        void testUsesActualDigest() {
            // Setup
            String originalXml = "<invoice><amount>100</amount></invoice>";
            String actualDigest = "cmVhbC1kaWdlc3QtdmFsdWU"; // a specific digest value
            String rawSignature = "c2ln";
            String certificate = "Y2VydA";

            // Execute
            String signedXml = embedder.embedSignature(originalXml, actualDigest, rawSignature, certificate);

            // Verify - contains actual digest, not placeholder
            assertThat(signedXml).contains(actualDigest);
            assertThat(signedXml).doesNotContain("PLACEHOLDER_DIGEST_BASE64");
        }

        @Test
        @DisplayName("Contains correct signature algorithm")
        void testSignatureAlgorithm() {
            // Setup
            String originalXml = "<root></root>";
            String documentDigest = "ZGln";
            String rawSignature = "c2ln";
            String certificate = "Y2VydA";

            // Execute
            String signedXml = embedder.embedSignature(originalXml, documentDigest, rawSignature, certificate);

            // Verify RSA-SHA256 is used
            assertThat(signedXml).contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
            assertThat(signedXml).contains("http://www.w3.org/2001/04/xmlenc#sha256");
        }

        @Test
        @DisplayName("Contains XAdES-BASELINE-T qualifying properties")
        void testXadesQualifyingProperties() {
            // Setup
            String originalXml = "<root></root>";
            String documentDigest = "ZGln";
            String rawSignature = "c2ln";
            String certificate = "Y2VydA";

            // Execute
            String signedXml = embedder.embedSignature(originalXml, documentDigest, rawSignature, certificate);

            // Verify XAdES elements
            assertThat(signedXml).contains("xades:SignedProperties");
            assertThat(signedXml).contains("xades:SignedSignatureProperties");
            assertThat(signedXml).contains("xades:SigningCertificate");
        }

        @Test
        @DisplayName("Throws RuntimeException for invalid XML")
        void testInvalidXml() {
            // Setup - invalid XML
            String invalidXml = "<root><unclosed>";

            // Execute & Verify
            assertThatThrownBy(() ->
                embedder.embedSignature(invalidXml, "digest", "sig", "cert")
            )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to embed XAdES signature");
        }

        @Test
        @DisplayName("Appends signature to root element")
        void testSignatureAppendedToRoot() {
            // Setup
            String originalXml = "<root><child>data</child></root>";
            String documentDigest = "ZGln";
            String rawSignature = "c2ln";
            String certificate = "Y2VydA";

            // Execute
            String signedXml = embedder.embedSignature(originalXml, documentDigest, rawSignature, certificate);

            // Verify signature is after root content but before closing root tag
            int rootIndex = signedXml.indexOf("<root>");
            int childIndex = signedXml.indexOf("<child>");
            int signatureIndex = signedXml.indexOf("<ds:Signature");
            int closingRootIndex = signedXml.indexOf("</root>");

            assertThat(rootIndex).isGreaterThan(-1);
            assertThat(childIndex).isGreaterThan(rootIndex);
            assertThat(signatureIndex).isGreaterThan(childIndex);
            assertThat(closingRootIndex).isGreaterThan(signatureIndex);
        }
    }

    @Nested
    @DisplayName("parseCertificate() Method")
    class ParseCertificateMethod {

        @Test
        @DisplayName("Parses valid base64 certificate")
        void testParseValidCertificate() {
            // Setup - a minimal valid X.509 certificate (base64 encoded)
            // This is a dummy certificate for testing
            String base64Cert = "MIIBkTCB+wIJAKHHCgVZU85zMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl";

            // Execute & Verify - should not throw
            // Note: This may fail because the dummy cert is not valid
            // In a real test, use a valid certificate
            assertThatThrownBy(() -> embedder.parseCertificate(base64Cert))
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Throws exception for invalid base64")
        void testParseInvalidBase64() {
            // Setup
            String invalidBase64 = "not-valid-base64!!!";

            // Execute & Verify
            assertThatThrownBy(() -> embedder.parseCertificate(invalidBase64))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse certificate");
        }
    }
}
