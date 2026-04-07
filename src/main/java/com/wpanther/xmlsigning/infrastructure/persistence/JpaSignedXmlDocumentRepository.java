package com.wpanther.xmlsigning.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SignedXmlDocumentEntity
 */
@Repository
public interface JpaSignedXmlDocumentRepository extends JpaRepository<SignedXmlDocumentEntity, UUID> {

    /**
     * Find entity by document ID
     */
    Optional<SignedXmlDocumentEntity> findByDocumentId(String documentId);

    /**
     * Find entity by document number
     */
    Optional<SignedXmlDocumentEntity> findByDocumentNumber(String documentNumber);

    /**
     * Check if entity exists by document ID
     */
    boolean existsByDocumentId(String documentId);

    /**
     * Delete a signed XML document by ID (for compensation)
     */
    @Modifying
    @Query("DELETE FROM SignedXmlDocumentEntity e WHERE e.id = :id")
    void deleteById(@Param("id") UUID id);

    /**
     * Find documents stuck in SIGNING state for longer than the given threshold.
     * These are likely orphaned due to TX2 failure after MinIO upload succeeded.
     *
     * @param threshold timestamp threshold
     * @return list of orphaned documents in SIGNING state
     */
    @Query("SELECT e FROM SignedXmlDocumentEntity e WHERE e.status = 'SIGNING' AND e.updatedAt < :threshold")
    List<SignedXmlDocumentEntity> findStuckInSigning(@Param("threshold") LocalDateTime threshold);

    /**
     * Find documents in FAILED state for longer than the given threshold.
     * These may have orphaned MinIO files from partial cleanup failures.
     *
     * @param threshold timestamp threshold
     * @return list of old failed documents
     */
    @Query("SELECT e FROM SignedXmlDocumentEntity e WHERE e.status = 'FAILED' AND e.updatedAt < :threshold")
    List<SignedXmlDocumentEntity> findOldFailedDocuments(@Param("threshold") LocalDateTime threshold);
}
