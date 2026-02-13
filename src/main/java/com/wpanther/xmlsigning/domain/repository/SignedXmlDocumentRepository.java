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
     * Find document by invoice ID
     */
    Optional<SignedXmlDocument> findByInvoiceId(String invoiceId);

    /**
     * Find document by invoice number
     */
    Optional<SignedXmlDocument> findByInvoiceNumber(String invoiceNumber);

    /**
     * Check if document exists by invoice ID
     */
    boolean existsByInvoiceId(String invoiceId);

    /**
     * Delete a signed XML document by ID (for compensation)
     */
    void deleteById(SignedXmlDocumentId id);
}
