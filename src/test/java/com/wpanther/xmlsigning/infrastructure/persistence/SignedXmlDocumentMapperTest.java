package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SignedXmlDocumentMapper}.
 * Uses Spring context to get the actual MapStruct generated implementation.
 */
@SpringBootTest(classes = {
        SignedXmlDocumentMapperImpl.class
})
@ActiveProfiles("test")
@DisplayName("SignedXmlDocumentMapper")
class SignedXmlDocumentMapperTest {

    @Autowired
    private SignedXmlDocumentMapper mapper;

    private static final String FAKE_ORIGINAL_S3_KEY = "2024/01/15/TAX_INVOICE/original-xml-inv-001-uuid.xml";
    private static final String FAKE_ORIGINAL_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_ORIGINAL_S3_KEY;
    private static final String FAKE_S3_KEY = "2024/01/15/TAX_INVOICE/signed-xml-inv-001-uuid.xml";
    private static final String FAKE_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_S3_KEY;
    private static final long   FAKE_SIZE   = 1234L;

    private SignedXmlDocument createDomain() {
        return SignedXmlDocument.builder()
                .id(SignedXmlDocumentId.create())
                .documentId("inv-001")
                .documentNumber("T001")
                .documentType(DocumentType.TAX_INVOICE)
                .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
                .originalXmlUrl(FAKE_ORIGINAL_URL)
                .signedXmlPath(FAKE_S3_KEY)
                .signedXmlUrl(FAKE_URL)
                .signedXmlSize(FAKE_SIZE)
                .transactionId("txn-1")
                .certificate("cert-data")
                .signatureLevel("XAdES-BASELINE-T")
                .status(SigningStatus.COMPLETED)
                .errorMessage(null)
                .retryCount(2)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }

    private SignedXmlDocumentEntity createEntity() {
        SignedXmlDocumentEntity entity = new SignedXmlDocumentEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentId("inv-001");
        entity.setDocumentNumber("T001");
        entity.setDocumentType(DocumentType.TAX_INVOICE);
        entity.setOriginalXmlPath(FAKE_ORIGINAL_S3_KEY);
        entity.setOriginalXmlUrl(FAKE_ORIGINAL_URL);
        entity.setSignedXmlPath(FAKE_S3_KEY);
        entity.setSignedXmlUrl(FAKE_URL);
        entity.setSignedXmlSize(FAKE_SIZE);
        entity.setTransactionId("txn-1");
        entity.setCertificate("cert-data");
        entity.setSignatureLevel("XAdES-BASELINE-T");
        entity.setStatus(SigningStatus.COMPLETED);
        entity.setRetryCount(2);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCompletedAt(LocalDateTime.now());
        return entity;
    }

    @Nested
    @DisplayName("toDomain() Method")
    class ToDomainMethod {

        @Test
        @DisplayName("Maps entity to domain")
        void testToDomain() {
            SignedXmlDocumentEntity entity = createEntity();

            SignedXmlDocument domain = mapper.toDomain(entity);

            assertThat(domain).isNotNull();
            assertThat(domain.getId().value()).isEqualTo(entity.getId());
            assertThat(domain.getDocumentId()).isEqualTo(entity.getDocumentId());
            assertThat(domain.getDocumentNumber()).isEqualTo(entity.getDocumentNumber());
            assertThat(domain.getDocumentType()).isEqualTo(entity.getDocumentType());
            assertThat(domain.getOriginalXmlPath()).isEqualTo(entity.getOriginalXmlPath());
            assertThat(domain.getOriginalXmlUrl()).isEqualTo(entity.getOriginalXmlUrl());
            assertThat(domain.getSignedXmlPath()).isEqualTo(entity.getSignedXmlPath());
            assertThat(domain.getSignedXmlUrl()).isEqualTo(entity.getSignedXmlUrl());
            assertThat(domain.getSignedXmlSize()).isEqualTo(entity.getSignedXmlSize());
            assertThat(domain.getTransactionId()).isEqualTo(entity.getTransactionId());
            assertThat(domain.getCertificate()).isEqualTo(entity.getCertificate());
            assertThat(domain.getSignatureLevel()).isEqualTo(entity.getSignatureLevel());
            assertThat(domain.getStatus()).isEqualTo(entity.getStatus());
            assertThat(domain.getRetryCount()).isEqualTo(entity.getRetryCount());
        }
    }

    @Nested
    @DisplayName("toEntity() Method")
    class ToEntityMethod {

        @Test
        @DisplayName("Maps domain to entity")
        void testToEntity() {
            SignedXmlDocument domain = createDomain();

            SignedXmlDocumentEntity entity = mapper.toEntity(domain);

            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isEqualTo(domain.getId().value());
            assertThat(entity.getDocumentId()).isEqualTo(domain.getDocumentId());
            assertThat(entity.getDocumentNumber()).isEqualTo(domain.getDocumentNumber());
            assertThat(entity.getDocumentType()).isEqualTo(domain.getDocumentType());
            assertThat(entity.getOriginalXmlPath()).isEqualTo(domain.getOriginalXmlPath());
            assertThat(entity.getOriginalXmlUrl()).isEqualTo(domain.getOriginalXmlUrl());
            assertThat(entity.getSignedXmlPath()).isEqualTo(domain.getSignedXmlPath());
            assertThat(entity.getSignedXmlUrl()).isEqualTo(domain.getSignedXmlUrl());
            assertThat(entity.getSignedXmlSize()).isEqualTo(domain.getSignedXmlSize());
            assertThat(entity.getTransactionId()).isEqualTo(domain.getTransactionId());
            assertThat(entity.getCertificate()).isEqualTo(domain.getCertificate());
            assertThat(entity.getSignatureLevel()).isEqualTo(domain.getSignatureLevel());
            assertThat(entity.getStatus()).isEqualTo(domain.getStatus());
            assertThat(entity.getRetryCount()).isEqualTo(domain.getRetryCount());
        }
    }

    @Nested
    @DisplayName("Round Trip")
    class RoundTrip {

        @Test
        @DisplayName("Entity to domain and back preserves values")
        void testRoundTripEntityToDomain() {
            SignedXmlDocumentEntity original = createEntity();

            SignedXmlDocument domain = mapper.toDomain(original);
            SignedXmlDocumentEntity result = mapper.toEntity(domain);

            assertThat(result.getDocumentId()).isEqualTo(original.getDocumentId());
            assertThat(result.getDocumentNumber()).isEqualTo(original.getDocumentNumber());
            assertThat(result.getDocumentType()).isEqualTo(original.getDocumentType());
            assertThat(result.getOriginalXmlPath()).isEqualTo(original.getOriginalXmlPath());
            assertThat(result.getOriginalXmlUrl()).isEqualTo(original.getOriginalXmlUrl());
            assertThat(result.getSignedXmlPath()).isEqualTo(original.getSignedXmlPath());
            assertThat(result.getSignedXmlUrl()).isEqualTo(original.getSignedXmlUrl());
            assertThat(result.getSignedXmlSize()).isEqualTo(original.getSignedXmlSize());
            assertThat(result.getStatus()).isEqualTo(original.getStatus());
            assertThat(result.getRetryCount()).isEqualTo(original.getRetryCount());
        }
    }
}
