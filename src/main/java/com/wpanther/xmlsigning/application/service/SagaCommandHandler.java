package com.wpanther.xmlsigning.application.service;

import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.SigningResult;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.messaging.EventPublisher;
import com.wpanther.xmlsigning.infrastructure.messaging.SagaReplyPublisher;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Handles saga commands from the orchestrator.
 *
 * <p>Transaction strategy (H1 fix): DB connections are held only during short
 * read/write bursts. External I/O (CSC API signing, MinIO uploads) runs outside
 * any transaction so Hikari pool threads are never blocked during network calls.
 *
 * <p>Storage strategy (H2 fix): original XML is uploaded to MinIO before signing
 * so the {@code signed_xml_documents} table never stores large TEXT payloads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final SignedXmlDocumentRepository documentRepository;
    private final XmlSigningService signingService;
    private final DocumentTypeDetectionService documentTypeDetectionService;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final EventPublisher eventPublisher;
    private final MinioStorageService minioStorageService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.signing.max-retries:3}")
    private int maxRetries;

    /**
     * Handle a ProcessXmlSigningCommand from saga orchestrator.
     *
     * <p>Flow:
     * <ol>
     *   <li>Detect document type (pure logic, no I/O)</li>
     *   <li>Idempotency check — short DB read, no connection held after return</li>
     *   <li>Upload original XML to MinIO (external I/O, no transaction)</li>
     *   <li>TX1 — persist SIGNING state (short-lived connection)</li>
     *   <li>Sign XML via CSC API + upload signed XML to MinIO (no transaction)</li>
     *   <li>TX2 — persist COMPLETED + write both outbox events atomically</li>
     * </ol>
     */
    public void handleProcessCommand(ProcessXmlSigningCommand command) {
        log.info("Handling ProcessXmlSigningCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        // --- Phase 0: document type detection (pure logic) ---
        DocumentType documentType = resolveDocumentType(command);
        if (documentType == null) {
            log.error("Could not detect document type for saga {} document {}",
                    command.getSagaId(), command.getDocumentId());
            transactionTemplate.execute(s -> {
                sagaReplyPublisher.publishFailure(command.getSagaId(), command.getSagaStep(),
                        command.getCorrelationId(), "Document type detection failed");
                return null;
            });
            return;
        }

        // --- Phase 1: idempotency check (read-only, no connection held after return) ---
        Optional<SignedXmlDocument> existing =
                documentRepository.findByInvoiceId(command.getDocumentId());
        if (existing.isPresent() && existing.get().isSuccessful()) {
            log.warn("Document {} already signed, sending SUCCESS reply", command.getDocumentId());
            SignedXmlDocument completedDoc = existing.get();
            transactionTemplate.execute(s -> {
                sagaReplyPublisher.publishSuccess(command.getSagaId(), command.getSagaStep(),
                        command.getCorrelationId(),
                        completedDoc.getSignedXmlUrl(), completedDoc.getSignedXmlSize());
                return null;
            });
            return;
        }

        // --- Phase 2: upload original XML to MinIO if this is a new document (no transaction) ---
        final String originalXmlPath;
        final String originalXmlUrl;
        if (existing.isEmpty()) {
            try {
                originalXmlPath = minioStorageService.uploadOriginalXml(
                        command.getDocumentId(), documentType.name(), command.getXmlContent());
                originalXmlUrl = minioStorageService.buildUrl(originalXmlPath);
                log.info("Uploaded original XML to MinIO: key={}", originalXmlPath);
            } catch (Exception e) {
                log.error("Failed to upload original XML for saga {} document {}: {}",
                        command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
                transactionTemplate.execute(s -> {
                    sagaReplyPublisher.publishFailure(command.getSagaId(), command.getSagaStep(),
                            command.getCorrelationId(),
                            "Failed to store original XML: " + e.getMessage());
                    return null;
                });
                return;
            }
        } else {
            originalXmlPath = existing.get().getOriginalXmlPath();
            originalXmlUrl = existing.get().getOriginalXmlUrl();
        }

        // --- TX1: persist SIGNING state (short-lived DB connection) ---
        final DocumentType finalDocumentType = documentType;
        SignedXmlDocument document;
        try {
            document = transactionTemplate.execute(s -> {
                SignedXmlDocument doc = existing.orElseGet(() -> SignedXmlDocument.builder()
                        .invoiceId(command.getDocumentId())
                        .invoiceNumber(command.getInvoiceNumber())
                        .documentType(finalDocumentType)
                        .originalXmlPath(originalXmlPath)
                        .originalXmlUrl(originalXmlUrl)
                        .build());

                if (doc.isMaxRetriesExceeded(maxRetries)) {
                    log.error("Max retries exceeded for saga {} document {}",
                            command.getSagaId(), command.getDocumentId());
                    doc.markFailed("Maximum retry attempts exceeded");
                    documentRepository.save(doc);
                    sagaReplyPublisher.publishFailure(command.getSagaId(), command.getSagaStep(),
                            command.getCorrelationId(), "Maximum retry attempts exceeded");
                    return null; // signals caller to stop
                }

                doc.startSigning();
                return documentRepository.save(doc);
            });
        } catch (Exception e) {
            log.error("Failed to persist SIGNING state for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            transactionTemplate.execute(s -> {
                sagaReplyPublisher.publishFailure(command.getSagaId(), command.getSagaStep(),
                        command.getCorrelationId(), e.getMessage());
                return null;
            });
            return;
        }

        if (document == null) {
            // null return from TX1 means max-retries — reply already published inside TX1
            return;
        }

        // --- Phase 3: external I/O — CSC API signing + MinIO upload (no transaction held) ---
        final String signedXml;
        final String s3Key;
        final String signedXmlUrl;
        final long signedXmlSize;
        final String certificate;
        final String transactionId;
        try {
            var signingResult = signingService.signXml(command.getXmlContent(), document.getId().toString());
            signedXml = signingResult.signedXml();
            certificate = signingResult.certificate();
            transactionId = signingResult.transactionId();

            s3Key = minioStorageService.upload(
                    command.getDocumentId(), finalDocumentType.name(), signedXml);
            signedXmlUrl = minioStorageService.buildUrl(s3Key);
            signedXmlSize = signedXml.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            log.error("Failed to sign XML for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            final SignedXmlDocument failedDoc = document;
            transactionTemplate.execute(s -> {
                failedDoc.markFailed(e.getMessage());
                failedDoc.incrementRetryCount();
                documentRepository.save(failedDoc);
                sagaReplyPublisher.publishFailure(command.getSagaId(), command.getSagaStep(),
                        command.getCorrelationId(), e.getMessage());
                return null;
            });
            return;
        }

        // --- TX2: persist COMPLETED + publish both outbox events atomically ---
        final SignedXmlDocument completedDoc = document;
        transactionTemplate.execute(s -> {
            completedDoc.markCompleted(
                    s3Key, signedXmlUrl, signedXmlSize,
                    transactionId, certificate, "XAdES-BASELINE-T");
            documentRepository.save(completedDoc);

            eventPublisher.publishXmlSigned(new XmlSignedEvent(
                    command.getDocumentId(), command.getInvoiceNumber(),
                    finalDocumentType.name(), command.getCorrelationId()));

            sagaReplyPublisher.publishSuccess(command.getSagaId(), command.getSagaStep(),
                    command.getCorrelationId(), signedXmlUrl, signedXmlSize);
            return null;
        });

        log.info("Successfully processed XML signing for saga {} document {}",
                command.getSagaId(), command.getDocumentId());
    }

    /**
     * Handle a CompensateXmlSigningCommand from saga orchestrator.
     * Deletes both the original and signed XML from MinIO, removes the DB record,
     * and sends a COMPENSATED reply.
     *
     * <p>Transaction strategy: MinIO deletions happen outside any transaction.
     * Only the DB delete and outbox event are wrapped in a short transaction.
     */
    public void handleCompensation(CompensateXmlSigningCommand command) {
        log.info("Handling compensation for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        // Phase 1: Read document to get MinIO paths (short read-only transaction)
        final Optional<SignedXmlDocument> existing;
        try {
            existing = documentRepository.findByInvoiceId(command.getDocumentId());
        } catch (Exception e) {
            log.error("Failed to find document for compensation: saga {} document {}",
                    command.getSagaId(), command.getDocumentId(), e);
            publishCompensationFailure(command, "Failed to lookup document: " + e.getMessage());
            return;
        }

        if (existing.isEmpty()) {
            log.info("No SignedXmlDocument found for document {} - already compensated or never processed",
                    command.getDocumentId());
            // Still publish compensated (idempotent)
            transactionTemplate.execute(s -> {
                sagaReplyPublisher.publishCompensated(
                        command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
                return null;
            });
            return;
        }

        SignedXmlDocument doc = existing.get();
        final String originalXmlPath = doc.getOriginalXmlPath();
        final String signedXmlPath = doc.getSignedXmlPath();

        // Phase 2: Delete from MinIO outside transaction (external I/O)
        try {
            if (originalXmlPath != null) {
                minioStorageService.delete(originalXmlPath);
                log.info("Deleted original XML from MinIO: {}", originalXmlPath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete original XML from MinIO: {}, proceeding with DB cleanup",
                    originalXmlPath, e);
            // Continue with cleanup even if MinIO deletion fails
        }

        try {
            if (signedXmlPath != null) {
                minioStorageService.delete(signedXmlPath);
                log.info("Deleted signed XML from MinIO: {}", signedXmlPath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete signed XML from MinIO: {}, proceeding with DB cleanup",
                    signedXmlPath, e);
            // Continue with cleanup even if MinIO deletion fails
        }

        // Phase 3: Delete DB record and publish reply (short transaction)
        try {
            transactionTemplate.execute(s -> {
                documentRepository.deleteById(doc.getId());
                sagaReplyPublisher.publishCompensated(
                        command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
                return null;
            });
            log.info("Deleted SignedXmlDocument {} for compensation", doc.getId());
        } catch (Exception e) {
            log.error("Failed to delete document from database for compensation: saga {} document {}",
                    command.getSagaId(), command.getDocumentId(), e);
            publishCompensationFailure(command, "Failed to delete document: " + e.getMessage());
        }
    }

    private void publishCompensationFailure(CompensateXmlSigningCommand command, String errorMessage) {
        transactionTemplate.execute(s -> {
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                    "Compensation failed: " + errorMessage);
            return null;
        });
    }

    private DocumentType resolveDocumentType(ProcessXmlSigningCommand command) {
        if (command.getDocumentType() != null && !command.getDocumentType().isBlank()) {
            DocumentType type = DocumentType.fromName(command.getDocumentType());
            if (type != null) {
                log.info("Using document type from command: {} for document {}",
                        type, command.getDocumentId());
                return type;
            }
        }
        DocumentType detected =
                documentTypeDetectionService.detectFromXmlContent(command.getXmlContent());
        if (detected != null) {
            log.info("Detected document type from XML content: {} for document {}",
                    detected, command.getDocumentId());
        }
        return detected;
    }
}
