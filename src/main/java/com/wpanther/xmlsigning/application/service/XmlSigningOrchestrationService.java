package com.wpanther.xmlsigning.application.service;

import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.event.XmlSigningRequestedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Application service orchestrating XML signing workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XmlSigningOrchestrationService {

    private final SignedXmlDocumentRepository documentRepository;
    private final XmlSigningService signingService;
    private final DocumentTypeDetectionService documentTypeDetectionService;
    private final EventPublisher eventPublisher;

    @Value("${app.signing.max-retries:3}")
    private int maxRetries;

    /**
     * Process XML signing request
     */
    @Transactional
    public void processSigningRequest(XmlSigningRequestedEvent event) {
        log.info("Processing signing request for invoice: {}", event.getInvoiceNumber());

        try {
            // Check if already signed
            Optional<SignedXmlDocument> existing =
                documentRepository.findByInvoiceId(event.getInvoiceId());

            if (existing.isPresent() && existing.get().isSuccessful()) {
                log.warn("Invoice {} already signed, skipping", event.getInvoiceNumber());
                return;
            }

            // Detect document type from event or XML content
            final DocumentType documentType;
            DocumentType detectedType = event.getDocumentType();
            if (detectedType == null) {
                // Fallback to detection from XML content
                detectedType = documentTypeDetectionService.detectFromXmlContent(event.getXmlContent());
                if (detectedType == null) {
                    log.error("Could not detect document type for invoice: {}", event.getInvoiceNumber());
                    throw new IllegalStateException("Document type detection failed");
                }
                log.info("Detected document type from XML content: {} for invoice: {}",
                    detectedType, event.getInvoiceNumber());
            } else {
                log.info("Using document type from event: {} for invoice: {}",
                    detectedType, event.getInvoiceNumber());
            }
            documentType = detectedType;

            // Create or retrieve document
            SignedXmlDocument document = existing.orElseGet(() ->
                SignedXmlDocument.builder()
                    .invoiceId(event.getInvoiceId())
                    .invoiceNumber(event.getInvoiceNumber())
                    .documentType(documentType)
                    .originalXml(event.getXmlContent())
                    .build()
            );

            // Check retry limit
            if (document.isMaxRetriesExceeded(maxRetries)) {
                log.error("Max retries exceeded for invoice: {}", event.getInvoiceNumber());
                document.markFailed("Maximum retry attempts exceeded");
                documentRepository.save(document);
                return;
            }

            // Start signing
            document.startSigning();
            documentRepository.save(document);

            // Sign XML using CSC API
            String documentId = document.getId().toString();
            String signedXml = signingService.signXml(event.getXmlContent(), documentId);

            // Mark as completed (transactionId and certificate would come from CSC response)
            document.markCompleted(
                signedXml,
                "TXN-" + documentId, // This should come from CSC response
                null,                 // Certificate from CSC response
                "XAdES-BASELINE-T"
            );
            documentRepository.save(document);

            // Publish signed event with document type
            XmlSignedEvent signedEvent = new XmlSignedEvent(
                document.getId().toString(),
                event.getInvoiceId(),
                event.getInvoiceNumber(),
                signedXml,
                event.getInvoiceDataJson(),
                document.getTransactionId(),
                document.getCertificate(),
                document.getSignatureLevel(),
                event.getCorrelationId(),
                document.getDocumentType()
            );
            eventPublisher.publishXmlSigned(signedEvent);

            log.info("Successfully signed {} invoice: {}", documentType, event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to sign invoice: {}", event.getInvoiceNumber(), e);
            handleSigningFailure(event, e);
        }
    }

    /**
     * Handle signing failure with retry logic
     */
    private void handleSigningFailure(XmlSigningRequestedEvent event, Exception e) {
        Optional<SignedXmlDocument> document =
            documentRepository.findByInvoiceId(event.getInvoiceId());

        document.ifPresent(doc -> {
            doc.incrementRetryCount();
            doc.markFailed(e.getMessage());
            documentRepository.save(doc);
        });
    }

    /**
     * Find document by ID
     */
    @Transactional(readOnly = true)
    public Optional<SignedXmlDocument> findById(String id) {
        try {
            return documentRepository.findById(SignedXmlDocumentId.from(id));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document ID format: {}", id);
            return Optional.empty();
        }
    }
}
