package com.wpanther.xmlsigning.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Delete a signed XML document by ID (for compensation)
     */
    @Modifying
    @Query("DELETE FROM SignedXmlDocumentEntity e WHERE e.id = :id")
    void deleteById(@Param("id") UUID id);
}
