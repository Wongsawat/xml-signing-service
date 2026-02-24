package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensation command from Saga Orchestrator to rollback XML signing.
 * Consumed from Kafka topic: saga.compensation.xml-signing
 */
@Getter
public class CompensateXmlSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("stepToCompensate")
    private final String stepToCompensate;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
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
