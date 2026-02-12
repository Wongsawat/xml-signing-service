package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignedXmlDocumentRepositoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignedXmlDocumentRepositoryAdapter")
class SignedXmlDocumentRepositoryAdapterTest {

    @Mock
    private JpaSignedXmlDocumentRepository jpaRepository;

    @Mock
    private SignedXmlDocumentMapper mapper;

    @InjectMocks
    private SignedXmlDocumentRepositoryAdapter adapter;

    private SignedXmlDocument createDomainDoc() {
        return SignedXmlDocument.builder()
                .id(SignedXmlDocumentId.create())
                .invoiceId("inv-001")
                .invoiceNumber("T001")
                .documentType(DocumentType.TAX_INVOICE)
                .originalXml("<xml>test</xml>")
                .build();
    }

    private SignedXmlDocumentEntity createEntity() {
        SignedXmlDocumentEntity entity = new SignedXmlDocumentEntity();
        entity.setId(UUID.randomUUID());
        entity.setInvoiceId("inv-001");
        entity.setInvoiceNumber("T001");
        entity.setDocumentType(DocumentType.TAX_INVOICE);
        entity.setOriginalXml("<xml>test</xml>");
        entity.setStatus(SigningStatus.PENDING);
        entity.setRetryCount(0);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Nested
    @DisplayName("save() Method")
    class SaveMethod {

        @Test
        @DisplayName("Saves document and returns mapped domain object")
        void testSave() {
            // Setup
            SignedXmlDocument domainDoc = createDomainDoc();
            SignedXmlDocumentEntity entity = createEntity();
            SignedXmlDocumentEntity savedEntity = createEntity();
            SignedXmlDocument returnedDoc = createDomainDoc();

            when(mapper.toEntity(domainDoc)).thenReturn(entity);
            when(jpaRepository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDomain(savedEntity)).thenReturn(returnedDoc);

            // Execute
            SignedXmlDocument result = adapter.save(domainDoc);

            // Verify
            assertThat(result).isEqualTo(returnedDoc);
            verify(mapper).toEntity(domainDoc);
            verify(jpaRepository).save(entity);
            verify(mapper).toDomain(savedEntity);
        }
    }

    @Nested
    @DisplayName("findById() Method")
    class FindByIdMethod {

        @Test
        @DisplayName("Returns document when found")
        void testFindByIdFound() {
            // Setup
            SignedXmlDocumentId id = SignedXmlDocumentId.create();
            SignedXmlDocumentEntity entity = createEntity();
            entity.setId(id.value());
            SignedXmlDocument domainDoc = createDomainDoc();

            when(jpaRepository.findById(id.value())).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domainDoc);

            // Execute
            Optional<SignedXmlDocument> result = adapter.findById(id);

            // Verify
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(domainDoc);
        }

        @Test
        @DisplayName("Returns empty when not found")
        void testFindByIdNotFound() {
            // Setup
            SignedXmlDocumentId id = SignedXmlDocumentId.create();
            when(jpaRepository.findById(id.value())).thenReturn(Optional.empty());

            // Execute
            Optional<SignedXmlDocument> result = adapter.findById(id);

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByInvoiceId() Method")
    class FindByInvoiceIdMethod {

        @Test
        @DisplayName("Returns document when found")
        void testFindByInvoiceIdFound() {
            // Setup
            SignedXmlDocumentEntity entity = createEntity();
            SignedXmlDocument domainDoc = createDomainDoc();

            when(jpaRepository.findByInvoiceId("inv-001")).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domainDoc);

            // Execute
            Optional<SignedXmlDocument> result = adapter.findByInvoiceId("inv-001");

            // Verify
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(domainDoc);
        }

        @Test
        @DisplayName("Returns empty when not found")
        void testFindByInvoiceIdNotFound() {
            // Setup
            when(jpaRepository.findByInvoiceId("inv-001")).thenReturn(Optional.empty());

            // Execute
            Optional<SignedXmlDocument> result = adapter.findByInvoiceId("inv-001");

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByInvoiceNumber() Method")
    class FindByInvoiceNumberMethod {

        @Test
        @DisplayName("Returns document when found")
        void testFindByInvoiceNumberFound() {
            // Setup
            SignedXmlDocumentEntity entity = createEntity();
            SignedXmlDocument domainDoc = createDomainDoc();

            when(jpaRepository.findByInvoiceNumber("T001")).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domainDoc);

            // Execute
            Optional<SignedXmlDocument> result = adapter.findByInvoiceNumber("T001");

            // Verify
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(domainDoc);
        }

        @Test
        @DisplayName("Returns empty when not found")
        void testFindByInvoiceNumberNotFound() {
            // Setup
            when(jpaRepository.findByInvoiceNumber("T001")).thenReturn(Optional.empty());

            // Execute
            Optional<SignedXmlDocument> result = adapter.findByInvoiceNumber("T001");

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByInvoiceId() Method")
    class ExistsByInvoiceIdMethod {

        @Test
        @DisplayName("Returns true when exists")
        void testExistsByInvoiceIdTrue() {
            // Setup
            when(jpaRepository.existsByInvoiceId("inv-001")).thenReturn(true);

            // Execute
            boolean result = adapter.existsByInvoiceId("inv-001");

            // Verify
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when not exists")
        void testExistsByInvoiceIdFalse() {
            // Setup
            when(jpaRepository.existsByInvoiceId("inv-001")).thenReturn(false);

            // Execute
            boolean result = adapter.existsByInvoiceId("inv-001");

            // Verify
            assertThat(result).isFalse();
        }
    }
}
