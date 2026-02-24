package com.wpanther.xmlsigning.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Aggregate Root representing a signed XML document
 *
 * This aggregate encapsulates the XML signing lifecycle including
 * authorization, signing, and timestamp verification.
 */
public class SignedXmlDocument {

    // Identity
    private final SignedXmlDocumentId id;
    private final String invoiceId;
    private final String invoiceNumber;
    private final DocumentType documentType;

    // Original XML stored in MinIO (uploaded before signing)
    private final String originalXmlPath;   // S3 key
    private final String originalXmlUrl;    // full URL (optional, derived from path)

    // Signed XML stored in MinIO
    private String signedXmlPath;   // S3 key
    private String signedXmlUrl;    // full URL
    private long signedXmlSize;     // bytes

    // Signing Metadata
    private String transactionId;
    private String certificate;
    private String signatureLevel;

    // Status
    private SigningStatus status;
    private String errorMessage;
    private int retryCount;

    // Timestamps
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private SignedXmlDocument(Builder builder) {
        this.id = builder.id != null ? builder.id : SignedXmlDocumentId.create();
        this.invoiceId = Objects.requireNonNull(builder.invoiceId, "Invoice ID is required");
        this.invoiceNumber = Objects.requireNonNull(builder.invoiceNumber, "Invoice number is required");
        this.documentType = Objects.requireNonNull(builder.documentType, "Document type is required");
        this.originalXmlPath = Objects.requireNonNull(builder.originalXmlPath, "Original XML path is required");
        this.originalXmlUrl = builder.originalXmlUrl;
        this.signedXmlPath = builder.signedXmlPath;
        this.signedXmlUrl = builder.signedXmlUrl;
        this.signedXmlSize = builder.signedXmlSize;
        this.transactionId = builder.transactionId;
        this.certificate = builder.certificate;
        this.signatureLevel = builder.signatureLevel;
        this.status = builder.status != null ? builder.status : SigningStatus.PENDING;
        this.errorMessage = builder.errorMessage;
        this.retryCount = builder.retryCount;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.completedAt = builder.completedAt;

        validateInvariant();
    }

    /**
     * Validate business invariants
     */
    private void validateInvariant() {
        if (invoiceId.isBlank()) {
            throw new IllegalStateException("Invoice ID cannot be blank");
        }

        if (invoiceNumber.isBlank()) {
            throw new IllegalStateException("Invoice number cannot be blank");
        }

        if (documentType == null) {
            throw new IllegalStateException("Document type is required");
        }

        if (originalXmlPath.isBlank()) {
            throw new IllegalStateException("Original XML path cannot be blank");
        }

        if (retryCount < 0) {
            throw new IllegalStateException("Retry count cannot be negative");
        }
    }

    /**
     * Start signing process
     */
    public void startSigning() {
        if (this.status != SigningStatus.PENDING && this.status != SigningStatus.FAILED) {
            throw new IllegalStateException(
                "Can only start signing from PENDING or FAILED status, current: " + this.status);
        }
        this.status = SigningStatus.SIGNING;
    }

    /**
     * Mark signing as completed with MinIO storage details.
     */
    public void markCompleted(String signedXmlPath, String signedXmlUrl, long signedXmlSize,
                              String transactionId, String certificate, String signatureLevel) {
        if (this.status != SigningStatus.SIGNING) {
            throw new IllegalStateException("Can only complete from SIGNING status, current: " + this.status);
        }

        Objects.requireNonNull(signedXmlPath, "Signed XML path is required");
        Objects.requireNonNull(signedXmlUrl, "Signed XML URL is required");
        if (signedXmlSize <= 0) {
            throw new IllegalArgumentException("Signed XML size must be positive");
        }

        this.signedXmlPath = signedXmlPath;
        this.signedXmlUrl = signedXmlUrl;
        this.signedXmlSize = signedXmlSize;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.status = SigningStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark signing as failed
     */
    public void markFailed(String errorMessage) {
        this.status = SigningStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Check if max retries exceeded
     */
    public boolean isMaxRetriesExceeded(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    /**
     * Check if signing is successful
     */
    public boolean isSuccessful() {
        return status == SigningStatus.COMPLETED;
    }

    // Getters
    public SignedXmlDocumentId getId() {
        return id;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getOriginalXmlPath() {
        return originalXmlPath;
    }

    public String getOriginalXmlUrl() {
        return originalXmlUrl;
    }

    public String getSignedXmlPath() {
        return signedXmlPath;
    }

    public String getSignedXmlUrl() {
        return signedXmlUrl;
    }

    public long getSignedXmlSize() {
        return signedXmlSize;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCertificate() {
        return certificate;
    }

    public String getSignatureLevel() {
        return signatureLevel;
    }

    public SigningStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * Builder for SignedXmlDocument
     */
    public static class Builder {
        private SignedXmlDocumentId id;
        private String invoiceId;
        private String invoiceNumber;
        private DocumentType documentType;
        private String originalXmlPath;
        private String originalXmlUrl;
        private String signedXmlPath;
        private String signedXmlUrl;
        private long signedXmlSize;
        private String transactionId;
        private String certificate;
        private String signatureLevel;
        private SigningStatus status;
        private String errorMessage;
        private int retryCount;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        public Builder id(SignedXmlDocumentId id) {
            this.id = id;
            return this;
        }

        public Builder invoiceId(String invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public Builder documentType(DocumentType documentType) {
            this.documentType = documentType;
            return this;
        }

        public Builder originalXmlPath(String originalXmlPath) {
            this.originalXmlPath = originalXmlPath;
            return this;
        }

        public Builder originalXmlUrl(String originalXmlUrl) {
            this.originalXmlUrl = originalXmlUrl;
            return this;
        }

        public Builder signedXmlPath(String signedXmlPath) {
            this.signedXmlPath = signedXmlPath;
            return this;
        }

        public Builder signedXmlUrl(String signedXmlUrl) {
            this.signedXmlUrl = signedXmlUrl;
            return this;
        }

        public Builder signedXmlSize(long signedXmlSize) {
            this.signedXmlSize = signedXmlSize;
            return this;
        }

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder certificate(String certificate) {
            this.certificate = certificate;
            return this;
        }

        public Builder signatureLevel(String signatureLevel) {
            this.signatureLevel = signatureLevel;
            return this;
        }

        public Builder status(SigningStatus status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public SignedXmlDocument build() {
            return new SignedXmlDocument(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
