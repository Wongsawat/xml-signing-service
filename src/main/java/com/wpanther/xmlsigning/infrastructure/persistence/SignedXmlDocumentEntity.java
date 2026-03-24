package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for signed XML documents
 */
@Entity
@Table(name = "signed_xml_documents", indexes = {
    @Index(name = "idx_signed_xml_invoice_id", columnList = "invoice_id"),
    @Index(name = "idx_signed_xml_invoice_number", columnList = "invoice_number"),
    @Index(name = "idx_signed_xml_status", columnList = "status"),
    @Index(name = "idx_signed_xml_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_signed_xml_document_type", columnList = "document_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedXmlDocumentEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "invoice_id", nullable = false, unique = true, length = 100)
    private String invoiceId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "original_xml_path", nullable = false, length = 500)
    private String originalXmlPath;

    @Column(name = "original_xml_url", length = 1000)
    private String originalXmlUrl;

    @Column(name = "signed_xml_path", length = 500)
    private String signedXmlPath;

    @Column(name = "signed_xml_url", length = 1000)
    private String signedXmlUrl;

    @Column(name = "signed_xml_size_bytes")
    private Long signedXmlSize;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "certificate", columnDefinition = "TEXT")
    private String certificate;

    @Column(name = "signature_level", length = 50)
    private String signatureLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SigningStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignedXmlDocumentEntity that = (SignedXmlDocumentEntity) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }
}
