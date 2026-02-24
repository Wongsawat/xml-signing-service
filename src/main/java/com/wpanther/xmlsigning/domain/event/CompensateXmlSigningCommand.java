package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensation command from Saga Orchestrator to rollback XML signing.
 * Consumed from Kafka topic: saga.compensation.xml-signing
 *
 * <p>Validation is performed when the command is consumed from Kafka via
 * {@link com.wpanther.xmlsigning.infrastructure.messaging.SagaRouteConfig}.
 */
@Getter
public class CompensateXmlSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    /**
     * The saga step to compensate.
     */
    @JsonProperty("stepToCompensate")
    @NotBlank(message = "stepToCompensate is required")
    private final String stepToCompensate;

    /**
     * Unique identifier for the document to be compensated.
     * Must be non-blank and maximum 100 characters.
     */
    @JsonProperty("documentId")
    @NotBlank(message = "documentId is required")
    @Size(max = 100, message = "documentId exceeds maximum length of 100 characters")
    private final String documentId;

    /**
     * Document type hint (optional).
     * If provided, must match one of the known document types.
     */
    @JsonProperty("documentType")
    @Size(max = 50, message = "documentType exceeds maximum length of 50 characters")
    @Pattern(regexp = "^[A-Z_]+$", message = "documentType must contain only uppercase letters and underscores")
    private final String documentType;

    @JsonCreator
    public CompensateXmlSigningCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("stepToCompensate") String stepToCompensate,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }

    /**
     * Convenience constructor for testing.
     */
    public CompensateXmlSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                       String stepToCompensate, String documentId, String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }
}
