package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CSC API DTOs.
 */
@DisplayName("CSC DTOs")
class CSCDtoTest {

    @Nested
    @DisplayName("CSCAuthorizeRequest")
    class CSCAuthorizeRequestTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                    .clientId("client-1")
                    .credentialID("cred-1")
                    .numSignatures("1")
                    .hashAlgo("SHA256")
                    .hash(new String[]{"abc123", "def456"})
                    .validityPeriod(300L)
                    .description("Test signing")
                    .build();

            assertThat(request.getClientId()).isEqualTo("client-1");
            assertThat(request.getCredentialID()).isEqualTo("cred-1");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getHash()).isNotNull();
            assertThat(request.getHash()).hasSize(2);
            assertThat(request.getHash()[0]).isEqualTo("abc123");
            assertThat(request.getHash()[1]).isEqualTo("def456");
            assertThat(request.getValidityPeriod()).isEqualTo(300L);
            assertThat(request.getDescription()).isEqualTo("Test signing");
        }

        @Test
        @DisplayName("Builder without optional fields")
        void testBuilderWithoutOptionalFields() {
            CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                    .clientId("client-1")
                    .credentialID("cred-1")
                    .numSignatures("1")
                    .build();

            assertThat(request.getClientId()).isEqualTo("client-1");
            assertThat(request.getCredentialID()).isEqualTo("cred-1");
            assertThat(request.getNumSignatures()).isEqualTo("1");
            assertThat(request.getHash()).isNull();
            assertThat(request.getHashAlgo()).isNull();
            assertThat(request.getValidityPeriod()).isNull();
            assertThat(request.getDescription()).isNull();
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            CSCAuthorizeRequest request = new CSCAuthorizeRequest();
            request.setClientId("client-2");
            request.setCredentialID("cred-2");
            request.setNumSignatures("2");

            assertThat(request.getClientId()).isEqualTo("client-2");
            assertThat(request.getCredentialID()).isEqualTo("cred-2");
            assertThat(request.getNumSignatures()).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("CSCAuthorizeResponse")
    class CSCAuthorizeResponseTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            CSCAuthorizeResponse response = CSCAuthorizeResponse.builder()
                    .transactionID("txn-1")
                    .SAD("sad-token")
                    .expiresIn(300L)
                    .authMode("explicit")
                    .build();

            assertThat(response.getTransactionID()).isEqualTo("txn-1");
            assertThat(response.getSAD()).isEqualTo("sad-token");
            assertThat(response.getExpiresIn()).isEqualTo(300L);
            assertThat(response.getAuthMode()).isEqualTo("explicit");
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            CSCAuthorizeResponse response = new CSCAuthorizeResponse();
            response.setTransactionID("txn-2");
            response.setSAD("token-2");

            assertThat(response.getTransactionID()).isEqualTo("txn-2");
            assertThat(response.getSAD()).isEqualTo("token-2");
        }
    }

    @Nested
    @DisplayName("CSCSignDocumentRequest")
    class CSCSignDocumentRequestTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            SignatureAttributes attrs = SignatureAttributes.builder()
                    .signatureType("XAdES")
                    .build();

            CSCSignDocumentRequest request = CSCSignDocumentRequest.builder()
                    .clientId("client-1")
                    .credentialID("cred-1")
                    .SAD("sad-token")
                    .documentID("doc-1")
                    .document("base64doc")
                    .documentDigest("digest123")
                    .hashAlgo("SHA256")
                    .signatureAttributes(attrs)
                    .build();

            assertThat(request.getClientId()).isEqualTo("client-1");
            assertThat(request.getCredentialID()).isEqualTo("cred-1");
            assertThat(request.getSAD()).isEqualTo("sad-token");
            assertThat(request.getDocumentID()).isEqualTo("doc-1");
            assertThat(request.getDocument()).isEqualTo("base64doc");
            assertThat(request.getDocumentDigest()).isEqualTo("digest123");
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureAttributes()).isEqualTo(attrs);
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            CSCSignDocumentRequest request = new CSCSignDocumentRequest();
            request.setClientId("client-2");
            request.setDocumentID("doc-2");

            assertThat(request.getClientId()).isEqualTo("client-2");
            assertThat(request.getDocumentID()).isEqualTo("doc-2");
        }
    }

    @Nested
    @DisplayName("CSCSignDocumentResponse")
    class CSCSignDocumentResponseTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            CSCSignDocumentResponse response = CSCSignDocumentResponse.builder()
                    .transactionID("txn-1")
                    .signedDocument("signed-base64")
                    .certificate("cert-data")
                    .signatureAlgorithm("RSA-SHA256")
                    .build();

            assertThat(response.getTransactionID()).isEqualTo("txn-1");
            assertThat(response.getSignedDocument()).isEqualTo("signed-base64");
            assertThat(response.getCertificate()).isEqualTo("cert-data");
            assertThat(response.getSignatureAlgorithm()).isEqualTo("RSA-SHA256");
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            CSCSignDocumentResponse response = new CSCSignDocumentResponse();
            response.setTransactionID("txn-2");
            response.setSignedDocument("signed-2");

            assertThat(response.getTransactionID()).isEqualTo("txn-2");
            assertThat(response.getSignedDocument()).isEqualTo("signed-2");
        }
    }

    @Nested
    @DisplayName("SignatureAttributes")
    class SignatureAttributesTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            SignatureAttributes attrs = SignatureAttributes.builder()
                    .signatureType("XAdES")
                    .signatureLevel("XAdES-BASELINE-T")
                    .signatureForm("enveloped")
                    .digestAlgorithm("SHA256")
                    .signDate(1234567890L)
                    .build();

            assertThat(attrs.getSignatureType()).isEqualTo("XAdES");
            assertThat(attrs.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(attrs.getSignatureForm()).isEqualTo("enveloped");
            assertThat(attrs.getDigestAlgorithm()).isEqualTo("SHA256");
            assertThat(attrs.getSignDate()).isEqualTo(1234567890L);
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            SignatureAttributes attrs = new SignatureAttributes();
            attrs.setSignatureType("PAdES");
            attrs.setSignatureLevel("PAdES-BASELINE-T");

            assertThat(attrs.getSignatureType()).isEqualTo("PAdES");
            assertThat(attrs.getSignatureLevel()).isEqualTo("PAdES-BASELINE-T");
        }
    }

    @Nested
    @DisplayName("CSCSignatureRequest")
    class CSCSignatureRequestTests {

        @Test
        @DisplayName("Builder with SAD token (new authentication)")
        void testBuilderWithSAD() {
            SignatureAttributes attrs = SignatureAttributes.builder()
                    .signatureType("XAdES")
                    .build();

            SignatureData sigData = SignatureData.builder()
                    .hashToSign(new String[]{"abc123"})
                    .signatureAttributes(attrs)
                    .build();

            CSCSignatureRequest request = CSCSignatureRequest.builder()
                    .clientId("client-1")
                    .credentialID("cred-1")
                    .SAD("sad-token-xyz")
                    .hashAlgo("SHA256")
                    .signatureData(sigData)
                    .build();

            assertThat(request.getClientId()).isEqualTo("client-1");
            assertThat(request.getCredentialID()).isEqualTo("cred-1");
            assertThat(request.getSAD()).isEqualTo("sad-token-xyz");
            assertThat(request.getCredentials()).isNull(); // No PIN when using SAD
            assertThat(request.getHashAlgo()).isEqualTo("SHA256");
            assertThat(request.getSignatureData()).isEqualTo(sigData);
        }

        @Test
        @DisplayName("Builder with credentials.pin (deprecated authentication)")
        void testBuilderWithCredentials() {
            SignatureData sigData = SignatureData.builder()
                    .hashToSign(new String[]{"abc123"})
                    .build();

            CSCSignatureRequest.Credentials credentials = CSCSignatureRequest.Credentials.builder()
                    .pin(CSCSignatureRequest.Pin.builder().value("1234").build())
                    .build();

            CSCSignatureRequest request = CSCSignatureRequest.builder()
                    .clientId("client-1")
                    .credentialID("cred-1")
                    .credentials(credentials)
                    .hashAlgo("SHA256")
                    .signatureData(sigData)
                    .build();

            assertThat(request.getClientId()).isEqualTo("client-1");
            assertThat(request.getCredentialID()).isEqualTo("cred-1");
            assertThat(request.getSAD()).isNull();
            assertThat(request.getCredentials()).isNotNull();
            assertThat(request.getCredentials().getPin().getValue()).isEqualTo("1234");
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            CSCSignatureRequest request = new CSCSignatureRequest();
            request.setClientId("client-2");
            request.setCredentialID("cred-2");
            request.setSAD("sad-token-2");

            assertThat(request.getClientId()).isEqualTo("client-2");
            assertThat(request.getCredentialID()).isEqualTo("cred-2");
            assertThat(request.getSAD()).isEqualTo("sad-token-2");
        }

        @Test
        @DisplayName("Credentials nested class builder")
        void testCredentialsBuilder() {
            CSCSignatureRequest.Pin pin = CSCSignatureRequest.Pin.builder()
                    .value("9999")
                    .build();

            assertThat(pin.getValue()).isEqualTo("9999");

            CSCSignatureRequest.Credentials credentials = CSCSignatureRequest.Credentials.builder()
                    .pin(pin)
                    .build();

            assertThat(credentials.getPin()).isEqualTo(pin);
        }
    }

    @Nested
    @DisplayName("SignatureData")
    class SignatureDataTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            SignatureAttributes attrs = SignatureAttributes.builder()
                    .signatureType("XAdES")
                    .build();

            SignatureData sigData = SignatureData.builder()
                    .hashToSign(new String[]{"hash1", "hash2"})
                    .signatureAttributes(attrs)
                    .build();

            assertThat(sigData.getHashToSign()).isNotNull();
            assertThat(sigData.getHashToSign()).hasSize(2);
            assertThat(sigData.getHashToSign()[0]).isEqualTo("hash1");
            assertThat(sigData.getHashToSign()[1]).isEqualTo("hash2");
            assertThat(sigData.getSignatureAttributes()).isEqualTo(attrs);
        }

        @Test
        @DisplayName("No-args constructor and setters")
        void testNoArgsConstructor() {
            SignatureData sigData = new SignatureData();
            sigData.setHashToSign(new String[]{"hash1"});

            assertThat(sigData.getHashToSign()).isNotNull();
            assertThat(sigData.getHashToSign()[0]).isEqualTo("hash1");
        }
    }
}
