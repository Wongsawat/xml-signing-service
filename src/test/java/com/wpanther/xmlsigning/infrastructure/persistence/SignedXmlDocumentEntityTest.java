package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SignedXmlDocumentEntity}.
 * Tests Lombok builder, no-args constructor, and JPA lifecycle callbacks.
 */
@DisplayName("SignedXmlDocumentEntity")
class SignedXmlDocumentEntityTest {

    private static final String FAKE_S3_KEY = "2024/01/15/TAX_INVOICE/signed-xml-inv-001-uuid.xml";
    private static final String FAKE_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_S3_KEY;
    private static final long   FAKE_SIZE   = 1234L;

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder with all fields creates valid entity")
        void testBuilderWithAllFields() {
            UUID id = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime updatedAt = LocalDateTime.now();
            LocalDateTime completedAt = LocalDateTime.now();

            SignedXmlDocumentEntity entity = SignedXmlDocumentEntity.builder()
                    .id(id)
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>original</xml>")
                    .signedXmlPath(FAKE_S3_KEY)
                    .signedXmlUrl(FAKE_URL)
                    .signedXmlSize(FAKE_SIZE)
                    .transactionId("txn-1")
                    .certificate("cert-data")
                    .signatureLevel("XAdES-BASELINE-T")
                    .status(SigningStatus.COMPLETED)
                    .errorMessage(null)
                    .retryCount(2)
                    .createdAt(createdAt)
                    .completedAt(completedAt)
                    .updatedAt(updatedAt)
                    .build();

            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getInvoiceId()).isEqualTo("INV-001");
            assertThat(entity.getInvoiceNumber()).isEqualTo("T001");
            assertThat(entity.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
            assertThat(entity.getOriginalXml()).isEqualTo("<xml>original</xml>");
            assertThat(entity.getSignedXmlPath()).isEqualTo(FAKE_S3_KEY);
            assertThat(entity.getSignedXmlUrl()).isEqualTo(FAKE_URL);
            assertThat(entity.getSignedXmlSize()).isEqualTo(FAKE_SIZE);
            assertThat(entity.getTransactionId()).isEqualTo("txn-1");
            assertThat(entity.getCertificate()).isEqualTo("cert-data");
            assertThat(entity.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(entity.getStatus()).isEqualTo(SigningStatus.COMPLETED);
            assertThat(entity.getErrorMessage()).isNull();
            assertThat(entity.getRetryCount()).isEqualTo(2);
            assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
            assertThat(entity.getCompletedAt()).isEqualTo(completedAt);
            assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("Builder with default retryCount uses 0")
        void testBuilderDefaultRetryCount() {
            SignedXmlDocumentEntity entity = SignedXmlDocumentEntity.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.RECEIPT)
                    .originalXml("<xml/>")
                    .status(SigningStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            assertThat(entity.getRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("NoArgsConstructor and Setters")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("NoArgsConstructor creates entity with null fields")
        void testNoArgsConstructor() {
            SignedXmlDocumentEntity entity = new SignedXmlDocumentEntity();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getInvoiceId()).isNull();
            assertThat(entity.getInvoiceNumber()).isNull();
            assertThat(entity.getDocumentType()).isNull();
            assertThat(entity.getOriginalXml()).isNull();
            assertThat(entity.getSignedXmlPath()).isNull();
            assertThat(entity.getSignedXmlUrl()).isNull();
            assertThat(entity.getSignedXmlSize()).isNull();
            assertThat(entity.getTransactionId()).isNull();
            assertThat(entity.getCertificate()).isNull();
            assertThat(entity.getSignatureLevel()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getErrorMessage()).isNull();
            assertThat(entity.getRetryCount()).isEqualTo(0);
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getCompletedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("Setters work correctly")
        void testSetters() {
            SignedXmlDocumentEntity entity = new SignedXmlDocumentEntity();
            UUID id = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            entity.setId(id);
            entity.setInvoiceId("INV-002");
            entity.setInvoiceNumber("T002");
            entity.setDocumentType(DocumentType.INVOICE);
            entity.setOriginalXml("<xml>test</xml>");
            entity.setSignedXmlPath(FAKE_S3_KEY);
            entity.setSignedXmlUrl(FAKE_URL);
            entity.setSignedXmlSize(FAKE_SIZE);
            entity.setTransactionId("txn-2");
            entity.setCertificate("cert");
            entity.setSignatureLevel("XAdES-BASELINE-T");
            entity.setStatus(SigningStatus.COMPLETED);
            entity.setErrorMessage("error");
            entity.setRetryCount(3);
            entity.setCreatedAt(now);
            entity.setCompletedAt(now);
            entity.setUpdatedAt(now);

            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getInvoiceId()).isEqualTo("INV-002");
            assertThat(entity.getInvoiceNumber()).isEqualTo("T002");
            assertThat(entity.getDocumentType()).isEqualTo(DocumentType.INVOICE);
            assertThat(entity.getOriginalXml()).isEqualTo("<xml>test</xml>");
            assertThat(entity.getSignedXmlPath()).isEqualTo(FAKE_S3_KEY);
            assertThat(entity.getSignedXmlUrl()).isEqualTo(FAKE_URL);
            assertThat(entity.getSignedXmlSize()).isEqualTo(FAKE_SIZE);
            assertThat(entity.getTransactionId()).isEqualTo("txn-2");
            assertThat(entity.getCertificate()).isEqualTo("cert");
            assertThat(entity.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(entity.getStatus()).isEqualTo(SigningStatus.COMPLETED);
            assertThat(entity.getErrorMessage()).isEqualTo("error");
            assertThat(entity.getRetryCount()).isEqualTo(3);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getCompletedAt()).isEqualTo(now);
            assertThat(entity.getUpdatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("JPA Lifecycle Callbacks")
    class LifecycleCallbacks {

        @Test
        @DisplayName("@PrePersist sets createdAt and updatedAt")
        void testOnCreate() {
            SignedXmlDocumentEntity entity = SignedXmlDocumentEntity.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml/>")
                    .status(SigningStatus.PENDING)
                    .build();

            entity.onCreate();

            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("@PrePersist preserves existing createdAt")
        void testOnCreatePreservesExistingCreatedAt() {
            LocalDateTime originalCreatedAt = LocalDateTime.of(2024, 1, 1, 12, 0);

            SignedXmlDocumentEntity entity = SignedXmlDocumentEntity.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml/>")
                    .status(SigningStatus.PENDING)
                    .createdAt(originalCreatedAt)
                    .build();

            entity.onCreate();

            assertThat(entity.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(entity.getUpdatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotEqualTo(originalCreatedAt);
        }

        @Test
        @DisplayName("@PreUpdate updates updatedAt")
        void testOnUpdate() {
            SignedXmlDocumentEntity entity = SignedXmlDocumentEntity.builder()
                    .invoiceId("INV-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml/>")
                    .status(SigningStatus.PENDING)
                    .updatedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                    .build();

            entity.onUpdate();

            assertThat(entity.getUpdatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isAfter(LocalDateTime.of(2024, 1, 1, 12, 0));
        }
    }
}
