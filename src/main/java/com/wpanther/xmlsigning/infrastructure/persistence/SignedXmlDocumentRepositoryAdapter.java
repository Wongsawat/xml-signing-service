package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.port.out.SignedXmlDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter implementing domain repository using Spring Data JPA
 */
@Repository
@RequiredArgsConstructor
public class SignedXmlDocumentRepositoryAdapter implements SignedXmlDocumentRepository {

    private final JpaSignedXmlDocumentRepository jpaRepository;
    private final SignedXmlDocumentMapper mapper;

    @Override
    public SignedXmlDocument save(SignedXmlDocument document) {
        SignedXmlDocumentEntity entity = mapper.toEntity(document);
        SignedXmlDocumentEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<SignedXmlDocument> findById(SignedXmlDocumentId id) {
        return jpaRepository.findById(id.value())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<SignedXmlDocument> findByInvoiceId(String invoiceId) {
        return jpaRepository.findByInvoiceId(invoiceId)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<SignedXmlDocument> findByInvoiceNumber(String invoiceNumber) {
        return jpaRepository.findByInvoiceNumber(invoiceNumber)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByInvoiceId(String invoiceId) {
        return jpaRepository.existsByInvoiceId(invoiceId);
    }

    @Override
    public void deleteById(SignedXmlDocumentId id) {
        jpaRepository.deleteById(id.value());
    }
}
