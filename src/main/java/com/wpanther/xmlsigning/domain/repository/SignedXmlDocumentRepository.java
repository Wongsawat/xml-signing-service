package com.wpanther.xmlsigning.domain.repository;

import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;

import java.util.Optional;

/**
 * Repository interface for SignedXmlDocument aggregate
 */
public interface SignedXmlDocumentRepository {

    /**
     * Save a signed XML document
     */
    SignedXmlDocument save(SignedXmlDocument document);

    /**
     * Find document by ID
     */
    Optional<SignedXmlDocument> findById(SignedXmlDocumentId id);

    /**
     * Find document by document ID
     */
    Optional<SignedXmlDocument> findByDocumentId(String documentId);

    /**
     * Find document by document number
     */
    Optional<SignedXmlDocument> findByDocumentNumber(String documentNumber);

    /**
     * Check if document exists by document ID
     */
    boolean existsByDocumentId(String documentId);

    /**
     * Delete a signed XML document by ID (for compensation)
     */
    void deleteById(SignedXmlDocumentId id);
}
