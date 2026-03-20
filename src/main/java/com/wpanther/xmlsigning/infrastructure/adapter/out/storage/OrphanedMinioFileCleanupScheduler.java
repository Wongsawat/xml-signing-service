package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.model.SigningStatus;
import com.wpanther.xmlsigning.infrastructure.persistence.JpaSignedXmlDocumentRepository;
import com.wpanther.xmlsigning.infrastructure.persistence.SignedXmlDocumentEntity;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scheduled job that cleans up orphaned MinIO files left behind when:
 * <ol>
 *   <li>MinIO upload succeeds but the subsequent DB transaction (TX2) fails</li>
 *   <li>Compensation runs but MinIO deletion partially fails</li>
 *   <li>Documents get stuck in SIGNING state due to TX2 timeout</li>
 * </ol>
 *
 * <p>This scheduler runs two cleanup strategies:
 * <ul>
 *   <li><b>Stuck documents cleanup</b>: Finds documents in SIGNING state older than
 *       the threshold and deletes their MinIO files (original XML uploaded in Phase 2
 *       is tracked in DB, but signed XML from Phase 3 may be orphaned)</li>
 *   <li><b>Orphaned file reconciliation</b>: Lists all MinIO objects and compares
 *       against DB records. Files with no corresponding COMPLETED record are deleted.</li>
 * </ul>
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>{@code app.minio.cleanup.stuck-threshold-hours} — how many hours a SIGNING
 *       document must be stuck before cleanup (default: 1)</li>
 *   <li>{@code app.minio.cleanup.cron} — when to run (default: 02:00 daily
 *       in the JVM timezone)</li>
 *   <li>{@code app.minio.cleanup.enabled} — enable/disable the scheduler
 *       (default: true)</li>
 * </ul>
 *
 * <p>Failures increment {@code minio.cleanup.failure} counter so alerting can
 * detect when the cleanup is not working properly.
 */
@Component
@Slf4j
public class OrphanedMinioFileCleanupScheduler {

    private final JpaSignedXmlDocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;
    private final Counter cleanupFailureCounter;
    private final Counter orphanedFilesDeletedCounter;

    @Value("${app.minio.cleanup.stuck-threshold-hours:1}")
    private int stuckThresholdHours;

    @Value("${app.minio.cleanup.cron:0 0 2 * * *}")
    private String cleanupCron;

    @Value("${app.minio.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    public OrphanedMinioFileCleanupScheduler(JpaSignedXmlDocumentRepository documentRepository,
                                            MinioStorageService minioStorageService,
                                            MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.minioStorageService = minioStorageService;
        this.cleanupFailureCounter = Counter.builder("minio.cleanup.failure")
                .description("Number of times the MinIO orphaned file cleanup job failed")
                .register(meterRegistry);
        this.orphanedFilesDeletedCounter = Counter.builder("minio.cleanup.orphaned-files-deleted")
                .description("Total number of orphaned MinIO files deleted by the cleanup job")
                .register(meterRegistry);
    }

    @PostConstruct
    void logConfiguration() {
        if (stuckThresholdHours < 1) {
            throw new IllegalStateException(
                    "app.minio.cleanup.stuck-threshold-hours must be >= 1, got: " + stuckThresholdHours);
        }
        log.info("OrphanedMinioFileCleanupScheduler initialized: enabled={}, stuckThreshold={} hours, cron='{}'",
                cleanupEnabled, stuckThresholdHours, cleanupCron);
    }

    @Scheduled(cron = "${app.minio.cleanup.cron:0 0 2 * * *}")
    public void cleanupOrphanedFiles() {
        if (!cleanupEnabled) {
            log.debug("MinIO cleanup is disabled, skipping");
            return;
        }

        log.info("Starting MinIO orphaned file cleanup");
        int totalDeleted = 0;

        try {
            // Strategy 1: Clean up stuck documents in SIGNING state
            totalDeleted += cleanupStuckSigningDocuments();

            // Strategy 2: Reconcile MinIO objects against DB records
            totalDeleted += reconcileMinioFiles();

            log.info("MinIO orphaned file cleanup completed: deleted {} files", totalDeleted);
        } catch (Exception e) {
            cleanupFailureCounter.increment();
            log.error("MinIO orphaned file cleanup failed: {}", e.toString(), e);
        }
    }

    /**
     * Strategy 1: Find documents stuck in SIGNING state and clean up their MinIO files.
     * This handles the case where TX2 failed after MinIO upload succeeded.
     */
    @Transactional
    int cleanupStuckSigningDocuments() {
        LocalDateTime threshold = LocalDateTime.now().minus(stuckThresholdHours, ChronoUnit.HOURS);
        List<SignedXmlDocumentEntity> stuckDocuments = documentRepository.findStuckInSigning(threshold);

        if (stuckDocuments.isEmpty()) {
            log.debug("No stuck SIGNING documents found older than {} hours", stuckThresholdHours);
            return 0;
        }

        log.info("Found {} stuck documents in SIGNING state older than {} hours",
                stuckDocuments.size(), stuckThresholdHours);

        int deletedCount = 0;
        for (SignedXmlDocumentEntity doc : stuckDocuments) {
            try {
                // Delete original XML from MinIO (uploaded in Phase 2, tracked in DB)
                if (doc.getOriginalXmlPath() != null && !doc.getOriginalXmlPath().isBlank()) {
                    minioStorageService.delete(doc.getOriginalXmlPath());
                    log.info("Deleted orphaned original XML for stuck document {}: {}",
                            doc.getInvoiceId(), doc.getOriginalXmlPath());
                }

                // Note: signed_xml_path is NOT in DB when TX2 failed, so we can't delete it here.
                // It will be cleaned up by Strategy 2 (reconciliation).

                // Mark document as FAILED so it won't be retried
                doc.setStatus(SigningStatus.FAILED);
                doc.setErrorMessage("Orphaned: stuck in SIGNING state after " + stuckThresholdHours + " hours, MinIO cleanup triggered");
                documentRepository.save(doc);

                deletedCount++;
            } catch (Exception e) {
                log.warn("Failed to cleanup stuck document {}: {}", doc.getInvoiceId(), e.getMessage());
                // Continue with other documents
            }
        }

        return deletedCount;
    }

    /**
     * Strategy 2: List all MinIO objects and delete those not referenced by any COMPLETED DB record.
     * This handles the case where signed XML was uploaded but TX2 failed (path not in DB).
     */
    @Transactional
    int reconcileMinioFiles() {
        // Get all MinIO object keys
        List<String> minioKeys = minioStorageService.listAllObjectKeys();
        if (minioKeys.isEmpty()) {
            log.debug("No MinIO objects found");
            return 0;
        }

        // Get all COMPLETED document paths from DB
        Set<String> validOriginalPaths = new HashSet<>();
        Set<String> validSignedPaths = new HashSet<>();
        for (SignedXmlDocumentEntity doc : documentRepository.findAll()) {
            if (doc.getOriginalXmlPath() != null && !doc.getOriginalXmlPath().isBlank()) {
                validOriginalPaths.add(doc.getOriginalXmlPath());
            }
            if (doc.getSignedXmlPath() != null && !doc.getSignedXmlPath().isBlank()) {
                validSignedPaths.add(doc.getSignedXmlPath());
            }
        }

        int deletedCount = 0;
        for (String minioKey : minioKeys) {
            // Check if this MinIO key is referenced by any COMPLETED or in-progress document
            boolean isValid = validOriginalPaths.contains(minioKey) || validSignedPaths.contains(minioKey);

            if (!isValid) {
                // This is an orphaned file - delete it
                try {
                    minioStorageService.delete(minioKey);
                    log.info("Deleted orphaned MinIO file not referenced by any DB record: {}", minioKey);
                    deletedCount++;
                    orphanedFilesDeletedCounter.increment();
                } catch (Exception e) {
                    log.warn("Failed to delete orphaned MinIO file {}: {}", minioKey, e.getMessage());
                    // Continue with other files
                }
            }
        }

        log.debug("MinIO reconciliation: checked {} objects, deleted {} orphaned files",
                minioKeys.size(), deletedCount);
        return deletedCount;
    }
}