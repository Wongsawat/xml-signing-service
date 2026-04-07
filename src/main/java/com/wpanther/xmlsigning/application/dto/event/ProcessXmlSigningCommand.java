package com.wpanther.xmlsigning.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to process XML signing.
 * Consumed from Kafka topic: saga.command.xml-signing
 *
 * <p>Validation is performed when the command is consumed from Kafka via
 * {@link com.wpanther.xmlsigning.infrastructure.adapter.in.camel.SagaRouteConfig}.
 */
@Getter
public class ProcessXmlSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the document to be signed.
     * Must be non-blank and maximum 100 characters.
     */
    @JsonProperty("documentId")
    @NotBlank(message = "documentId is required")
    @Size(max = 100, message = "documentId exceeds maximum length of 100 characters")
    private final String documentId;

    /**
     * The XML content to be signed.
     * Must be non-null, at least 50 characters (minimum valid XML), and at most 10MB.
     */
    @JsonProperty("xmlContent")
    @NotNull(message = "xmlContent is required")
    @Size(min = 50, max = 10_000_000, message = "xmlContent must be between 50 and 10,000,000 characters")
    private final String xmlContent;

    /**
     * Document number for the document.
     * Must be non-blank and maximum 50 characters.
     */
    @JsonProperty("documentNumber")
    @NotBlank(message = "documentNumber is required")
    @Size(max = 50, message = "documentNumber exceeds maximum length of 50 characters")
    private final String documentNumber;

    /**
     * Document type hint (optional).
     * If provided, must match one of the known document types.
     */
    @JsonProperty("documentType")
    @Size(max = 50, message = "documentType exceeds maximum length of 50 characters")
    @Pattern(regexp = "^[A-Z_]+$", message = "documentType must contain only uppercase letters and underscores")
    private final String documentType;

    @JsonCreator
    public ProcessXmlSigningCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("documentNumber") String documentNumber,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessXmlSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                    String documentId, String xmlContent,
                                    String documentNumber, String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
    }
}
