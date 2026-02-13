package com.wpanther.xmlsigning.application.service;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.messaging.EventPublisher;
import com.wpanther.xmlsigning.infrastructure.messaging.SagaReplyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles saga commands from orchestrator.
 * Delegates business logic to XmlSigningService and sends replies.
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

    @Value("${app.signing.max-retries:3}")
    private int maxRetries;

    /**
     * Handle a ProcessXmlSigningCommand from saga orchestrator.
     * Processes XML signing and sends a SUCCESS or FAILURE reply.
     */
    @Transactional
    public void handleProcessCommand(ProcessXmlSigningCommand command) {
        log.info("Handling ProcessXmlSigningCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Detect document type from command or XML content
            final DocumentType documentType;
            DocumentType detectedType = null;

            // First, try to get from command's documentType
            if (command.getDocumentType() != null && !command.getDocumentType().isBlank()) {
                detectedType = DocumentType.fromName(command.getDocumentType());
            }

            // Fallback to detection from XML content
            if (detectedType == null) {
                detectedType = documentTypeDetectionService.detectFromXmlContent(command.getXmlContent());
                if (detectedType == null) {
                    log.error("Could not detect document type for saga {} document {}",
                            command.getSagaId(), command.getDocumentId());
                    sagaReplyPublisher.publishFailure(
                            command.getSagaId(),
                            command.getSagaStep(),
                            command.getCorrelationId(),
                            "Document type detection failed"
                    );
                    return;
                }
                log.info("Detected document type from XML content: {} for saga {} document {}",
                                detectedType, command.getSagaId(), command.getDocumentId());
            } else {
                log.info("Using document type from command: {} for saga {} document {}",
                                detectedType, command.getSagaId(), command.getDocumentId());
            }

            documentType = detectedType;

            // Check if already signed
            Optional<SignedXmlDocument> existing =
                    documentRepository.findByInvoiceId(command.getDocumentId());

            if (existing.isPresent() && existing.get().isSuccessful()) {
                log.warn("Document {} already signed, sending SUCCESS reply", command.getDocumentId());
                sagaReplyPublisher.publishSuccess(
                        command.getSagaId(),
                        command.getSagaStep(),
                        command.getCorrelationId()
                );
                return;
            }

            // Create or retrieve document
            SignedXmlDocument document = existing.orElseGet(() ->
                    SignedXmlDocument.builder()
                            .invoiceId(command.getDocumentId())
                            .invoiceNumber(command.getInvoiceNumber())
                            .documentType(documentType)
                            .originalXml(command.getXmlContent())
                            .build()
            );

            // Check retry limit
            if (document.isMaxRetriesExceeded(maxRetries)) {
                log.error("Max retries exceeded for saga {} document {}", command.getSagaId(), command.getDocumentId());
                sagaReplyPublisher.publishFailure(
                        command.getSagaId(),
                        command.getSagaStep(),
                        command.getCorrelationId(),
                        "Maximum retry attempts exceeded"
                );
                document.markFailed("Maximum retry attempts exceeded");
                documentRepository.save(document);
                return;
            }

            // Start signing
            document.startSigning();
            documentRepository.save(document);

            // Sign XML using CSC API
            String documentId = document.getId().toString();
            String signedXml = signingService.signXml(command.getXmlContent(), documentId);

            // Mark as completed
            document.markCompleted(
                    signedXml,
                    "TXN-" + documentId,     // Transaction ID from CSC response would be better
                    null,                          // Certificate from CSC response
                    "XAdES-BASELINE-T"
            );
            documentRepository.save(document);

            // Publish notification event
            XmlSignedEvent xmlSignedEvent = new XmlSignedEvent(
                    command.getDocumentId(),
                    command.getInvoiceNumber(),
                    documentType.name(),
                    command.getCorrelationId()
            );
            eventPublisher.publishXmlSigned(xmlSignedEvent);

            // Send SUCCESS reply
            sagaReplyPublisher.publishSuccess(
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId()
            );

            log.info("Successfully processed XML signing for saga {} document {}",
                            command.getSagaId(), command.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to process XML signing for saga {} document {}: {}",
                            command.getSagaId(), command.getDocumentId(), e.getMessage(), e);

            // Send FAILURE reply
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId(),
                    e.getMessage()
            );
        }
    }

    /**
     * Handle a CompensateXmlSigningCommand from saga orchestrator.
     * Deletes signed XML document and sends a COMPENSATED reply.
     */
    @Transactional
    public void handleCompensation(CompensateXmlSigningCommand command) {
        log.info("Handling compensation for saga {} document {}",
                        command.getSagaId(), command.getDocumentId());

        try {
            Optional<SignedXmlDocument> existing =
                    documentRepository.findByInvoiceId(command.getDocumentId());

            if (existing.isPresent()) {
                    SignedXmlDocument doc = existing.get();
                    documentRepository.deleteById(doc.getId());
                    log.info("Deleted SignedXmlDocument {} for compensation", doc.getId());
            } else {
                log.info("No SignedXmlDocument found for document {} - already compensated or never processed",
                                command.getDocumentId());
            }

            // Send COMPENSATED reply (idempotent - safe to call multiple times)
            sagaReplyPublisher.publishCompensated(
                        command.getSagaId(),
                        command.getSagaStep(),
                        command.getCorrelationId()
            );

        } catch (Exception e) {
            log.error("Failed to compensate XML signing for saga {} document {}: {}",
                            command.getSagaId(), command.getDocumentId(), e.getMessage(), e);

            // Send FAILURE reply
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(),
                    command.getSagaStep(),
                    command.getCorrelationId(),
                    "Compensation failed: " + e.getMessage()
            );
        }
    }
}
