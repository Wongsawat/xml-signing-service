package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
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
    public Optional<SignedXmlDocument> findByDocumentId(String documentId) {
        return jpaRepository.findByDocumentId(documentId)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<SignedXmlDocument> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByDocumentId(String documentId) {
        return jpaRepository.existsByDocumentId(documentId);
    }

    @Override
    public void deleteById(SignedXmlDocumentId id) {
        jpaRepository.deleteById(id.value());
    }
}
