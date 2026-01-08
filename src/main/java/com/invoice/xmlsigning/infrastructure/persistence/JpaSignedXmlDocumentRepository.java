package com.invoice.xmlsigning.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SignedXmlDocumentEntity
 */
@Repository
public interface JpaSignedXmlDocumentRepository extends JpaRepository<SignedXmlDocumentEntity, UUID> {

    /**
     * Find entity by invoice ID
     */
    Optional<SignedXmlDocumentEntity> findByInvoiceId(String invoiceId);

    /**
     * Find entity by invoice number
     */
    Optional<SignedXmlDocumentEntity> findByInvoiceNumber(String invoiceNumber);

    /**
     * Check if entity exists by invoice ID
     */
    boolean existsByInvoiceId(String invoiceId);
}
