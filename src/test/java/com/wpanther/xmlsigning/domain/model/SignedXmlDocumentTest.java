package com.wpanther.xmlsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SignedXmlDocument} aggregate root.
 * Tests state machine transitions, builder validation, and business invariants.
 */
@DisplayName("SignedXmlDocument Aggregate")
class SignedXmlDocumentTest {

    private static final String FAKE_S3_KEY = "2024/01/15/TAX_INVOICE/signed-xml-inv-001-uuid.xml";
    private static final String FAKE_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_S3_KEY;
    private static final long   FAKE_SIZE   = 1234L;

    // ==================== Helper Methods ====================

    /**
     * Builds a valid SignedXmlDocument with all required fields.
     */
    private SignedXmlDocument buildDefault() {
        return SignedXmlDocument.builder()
                .invoiceId("INV-001")
                .invoiceNumber("T001")
                .documentType(DocumentType.TAX_INVOICE)
                .originalXml("<xml>test</xml>")
                .build();
    }

    /**
     * Builds a valid document with explicit ID.
     */
    private SignedXmlDocument buildWithId() {
        return SignedXmlDocument.builder()
                .id(SignedXmlDocumentId.create())
                .invoiceId("INV-001")
                .invoiceNumber("T001")
                .documentType(DocumentType.TAX_INVOICE)
                .originalXml("<xml>test</xml>")
                .build();
    }

    /**
     * Builds a document in FAILED state.
     */
    private SignedXmlDocument buildFailed() {
        return SignedXmlDocument.builder()
                .id(SignedXmlDocumentId.create())
                .invoiceId("INV-001")
                .invoiceNumber("T001")
                .documentType(DocumentType.TAX_INVOICE)
                .originalXml("<xml>test</xml>")
                .status(SigningStatus.FAILED)
                .errorMessage("Signing failed")
                .retryCount(2)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    // ==================== Builder Tests ====================

    @Nested
    @DisplayName("Builder with Defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("Builder with minimum required fields has default values")
        void builderWithDefaults() {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build();

            assertThat(doc.getId()).isNotNull();
            assertThat(doc.getInvoiceId()).isEqualTo("INV-001");
            assertThat(doc.getInvoiceNumber()).isEqualTo("T001");
            assertThat(doc.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
            assertThat(doc.getOriginalXml()).isEqualTo("<xml>test</xml>");
            assertThat(doc.getStatus()).isEqualTo(SigningStatus.PENDING);
            assertThat(doc.getRetryCount()).isZero();
            assertThat(doc.getCreatedAt()).isNotNull();
            assertThat(doc.getSignedXmlPath()).isNull();
            assertThat(doc.getSignedXmlUrl()).isNull();
            assertThat(doc.getSignedXmlSize()).isZero();
            assertThat(doc.getTransactionId()).isNull();
            assertThat(doc.getCertificate()).isNull();
            assertThat(doc.getSignatureLevel()).isNull();
            assertThat(doc.getCompletedAt()).isNull();
            assertThat(doc.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("Builder with explicit ID")
        void builderWithExplicitId() {
            SignedXmlDocumentId id = SignedXmlDocumentId.create();
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .id(id)
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build();

            assertThat(doc.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Builder with explicit FAILED status")
        void builderWithExplicitStatus() {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .status(SigningStatus.FAILED)
                    .build();

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.FAILED);
        }

        @Test
        @DisplayName("Builder with all fields set")
        void builderWithAllFields() {
            LocalDateTime now = LocalDateTime.now();
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .signedXmlPath(FAKE_S3_KEY)
                    .signedXmlUrl(FAKE_URL)
                    .signedXmlSize(FAKE_SIZE)
                    .transactionId("txn-123")
                    .certificate("cert-data")
                    .signatureLevel("XAdES-BASELINE-T")
                    .errorMessage("error msg")
                    .retryCount(2)
                    .completedAt(now)
                    .build();

            assertThat(doc.getSignedXmlPath()).isEqualTo(FAKE_S3_KEY);
            assertThat(doc.getSignedXmlUrl()).isEqualTo(FAKE_URL);
            assertThat(doc.getSignedXmlSize()).isEqualTo(FAKE_SIZE);
            assertThat(doc.getTransactionId()).isEqualTo("txn-123");
            assertThat(doc.getCertificate()).isEqualTo("cert-data");
            assertThat(doc.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(doc.getErrorMessage()).isEqualTo("error msg");
            assertThat(doc.getRetryCount()).isEqualTo(2);
            assertThat(doc.getCompletedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("Builder Validation - Null Checks")
    class BuilderNullChecks {

        @Test
        @DisplayName("Builder with null invoiceId throws NullPointerException")
        void builderNullInvoiceId() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId(null)
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build())
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("Invoice ID is required");
        }

        @Test
        @DisplayName("Builder with null invoiceNumber throws NullPointerException")
        void builderNullInvoiceNumber() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber(null)
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build())
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("Invoice number is required");
        }

        @Test
        @DisplayName("Builder with null documentType throws NullPointerException")
        void builderNullDocumentType() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(null)
                    .originalXml("<xml>test</xml>")
                    .build())
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("Document type is required");
        }

        @Test
        @DisplayName("Builder with null originalXml throws NullPointerException")
        void builderNullOriginalXml() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml(null)
                    .build())
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessageContaining("Original XML is required");
        }
    }

    @Nested
    @DisplayName("Builder Validation - Blank Checks")
    class BuilderBlankChecks {

        @Test
        @DisplayName("Builder with blank invoiceId throws IllegalStateException")
        void builderBlankInvoiceId() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("   ")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invoice ID cannot be blank");
        }

        @Test
        @DisplayName("Builder with blank invoiceNumber throws IllegalStateException")
        void builderBlankInvoiceNumber() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invoice number cannot be blank");
        }

        @Test
        @DisplayName("Builder with blank originalXml throws IllegalStateException")
        void builderBlankOriginalXml() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("   ")
                    .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Original XML cannot be blank");
        }
    }

    @Nested
    @DisplayName("Builder Validation - Invalid Values")
    class BuilderInvalidChecks {

        @Test
        @DisplayName("Builder with negative retry count throws IllegalStateException")
        void builderNegativeRetryCount() {
            assertThatThrownBy(() -> SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .retryCount(-1)
                    .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry count cannot be negative");
        }
    }

    // ==================== State Machine Tests ====================

    @Nested
    @DisplayName("State Machine Transitions")
    class StateMachine {

        @Test
        @DisplayName("startSigning() from PENDING changes status to SIGNING")
        void testStartSigningFromPending() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.SIGNING);
        }

        @Test
        @DisplayName("startSigning() from FAILED changes status to SIGNING")
        void testStartSigningFromFailed() {
            SignedXmlDocument doc = buildFailed();
            doc.startSigning();

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.SIGNING);
        }

        @Test
        @DisplayName("startSigning() from SIGNING throws IllegalStateException")
        void testStartSigningFromSigningThrows() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning(); // First call succeeds
            assertThatThrownBy(() -> doc.startSigning())
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only start signing from PENDING or FAILED");
        }

        @Test
        @DisplayName("startSigning() from COMPLETED throws IllegalStateException")
        void testStartSigningFromCompletedThrows() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();
            doc.markCompleted(FAKE_S3_KEY, FAKE_URL, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T");
            assertThatThrownBy(() -> doc.startSigning())
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only start signing from PENDING or FAILED");
        }

        @Test
        @DisplayName("markCompleted() from SIGNING succeeds")
        void testMarkCompletedFromSigning() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            doc.markCompleted(FAKE_S3_KEY, FAKE_URL, FAKE_SIZE, "txn-123", "cert-data", "XAdES-BASELINE-T");

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.COMPLETED);
            assertThat(doc.getSignedXmlPath()).isEqualTo(FAKE_S3_KEY);
            assertThat(doc.getSignedXmlUrl()).isEqualTo(FAKE_URL);
            assertThat(doc.getSignedXmlSize()).isEqualTo(FAKE_SIZE);
            assertThat(doc.getTransactionId()).isEqualTo("txn-123");
            assertThat(doc.getCertificate()).isEqualTo("cert-data");
            assertThat(doc.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(doc.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("markCompleted() from PENDING throws IllegalStateException")
        void testMarkCompletedFromPendingThrows() {
            SignedXmlDocument doc = buildDefault();

            assertThatThrownBy(() -> doc.markCompleted(FAKE_S3_KEY, FAKE_URL, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T"))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only complete from SIGNING");
        }

        @Test
        @DisplayName("markCompleted() from FAILED throws IllegalStateException")
        void testMarkCompletedFromFailedThrows() {
            SignedXmlDocument doc = buildFailed();

            assertThatThrownBy(() -> doc.markCompleted(FAKE_S3_KEY, FAKE_URL, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T"))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only complete from SIGNING");
        }

        @Test
        @DisplayName("markCompleted() with null signedXmlPath throws NullPointerException")
        void testMarkCompletedNullSignedXmlPath() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            assertThatThrownBy(() -> doc.markCompleted(null, FAKE_URL, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T"))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Signed XML path is required");
        }

        @Test
        @DisplayName("markCompleted() with null signedXmlUrl throws NullPointerException")
        void testMarkCompletedNullSignedXmlUrl() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            assertThatThrownBy(() -> doc.markCompleted(FAKE_S3_KEY, null, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T"))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Signed XML URL is required");
        }

        @Test
        @DisplayName("markCompleted() with zero size throws IllegalArgumentException")
        void testMarkCompletedZeroSize() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            assertThatThrownBy(() -> doc.markCompleted(FAKE_S3_KEY, FAKE_URL, 0L, "txn-1", "cert", "XAdES-BASELINE-T"))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Signed XML size must be positive");
        }

        @Test
        @DisplayName("markFailed() from any state succeeds")
        void testMarkFailed() {
            SignedXmlDocument doc = buildDefault();
            LocalDateTime before = LocalDateTime.now();

            doc.markFailed("Signing failed");

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.FAILED);
            assertThat(doc.getErrorMessage()).isEqualTo("Signing failed");
            assertThat(doc.getCompletedAt()).isNotNull();
            assertThat(doc.getCompletedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("markFailed() from SIGNING state also succeeds")
        void testMarkFailedFromSigning() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();

            doc.markFailed("Error occurred");

            assertThat(doc.getStatus()).isEqualTo(SigningStatus.FAILED);
            assertThat(doc.getErrorMessage()).isEqualTo("Error occurred");
        }

        @Test
        @DisplayName("incrementRetryCount() increments retry count")
        void testIncrementRetryCount() {
            SignedXmlDocument doc = buildDefault();
            doc.incrementRetryCount();
            doc.incrementRetryCount();

            assertThat(doc.getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("isMaxRetriesExceeded() returns true when retryCount >= maxRetries")
        void testIsMaxRetriesExceeded() {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .retryCount(3)
                    .build();

            assertThat(doc.isMaxRetriesExceeded(3)).isTrue();
        }

        @Test
        @DisplayName("isMaxRetriesExceeded() returns false when retryCount < maxRetries")
        void testIsMaxRetriesNotExceeded() {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .retryCount(2)
                    .build();

            assertThat(doc.isMaxRetriesExceeded(3)).isFalse();
        }

        @Test
        @DisplayName("isMaxRetriesExceeded() returns false at boundary")
        void testIsMaxRetriesExceededAtBoundary() {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .retryCount(3)
                    .build();

            assertThat(doc.isMaxRetriesExceeded(4)).isFalse();
        }

        @Test
        @DisplayName("isSuccessful() returns true only when COMPLETED")
        void testIsSuccessfulCompleted() {
            SignedXmlDocument doc = buildDefault();
            doc.startSigning();
            doc.markCompleted(FAKE_S3_KEY, FAKE_URL, FAKE_SIZE, "txn-1", "cert", "XAdES-BASELINE-T");

            assertThat(doc.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("isSuccessful() returns false for other states")
        void testIsSuccessfulNotCompleted() {
            SignedXmlDocument doc = buildDefault();

            assertThat(doc.isSuccessful()).isFalse();
        }

        @Nested
        @DisplayName("Getters")
        class Getters {

            @Test
            @DisplayName("All getters return set values")
            void testAllGetters() {
                SignedXmlDocumentId id = SignedXmlDocumentId.create();
                LocalDateTime now = LocalDateTime.now();

                SignedXmlDocument doc = SignedXmlDocument.builder()
                        .id(id)
                        .invoiceId("INV-001")
                        .invoiceNumber("T001")
                        .documentType(DocumentType.TAX_INVOICE)
                        .originalXml("<xml>test</xml>")
                        .signedXmlPath(FAKE_S3_KEY)
                        .signedXmlUrl(FAKE_URL)
                        .signedXmlSize(FAKE_SIZE)
                        .transactionId("txn-1")
                        .certificate("cert-data")
                        .signatureLevel("XAdES-BASELINE-T")
                        .errorMessage("error msg")
                        .retryCount(2)
                        .completedAt(now)
                        .build();

                assertThat(doc.getId()).isEqualTo(id);
                assertThat(doc.getInvoiceId()).isEqualTo("INV-001");
                assertThat(doc.getInvoiceNumber()).isEqualTo("T001");
                assertThat(doc.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
                assertThat(doc.getOriginalXml()).isEqualTo("<xml>test</xml>");
                assertThat(doc.getSignedXmlPath()).isEqualTo(FAKE_S3_KEY);
                assertThat(doc.getSignedXmlUrl()).isEqualTo(FAKE_URL);
                assertThat(doc.getSignedXmlSize()).isEqualTo(FAKE_SIZE);
                assertThat(doc.getTransactionId()).isEqualTo("txn-1");
                assertThat(doc.getCertificate()).isEqualTo("cert-data");
                assertThat(doc.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
                assertThat(doc.getErrorMessage()).isEqualTo("error msg");
                assertThat(doc.getRetryCount()).isEqualTo(2);
                assertThat(doc.getCreatedAt()).isEqualTo(doc.getCreatedAt());
            }
        }
    }
}
