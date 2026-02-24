package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to process XML signing.
 * Consumed from Kafka topic: saga.command.xml-signing
 */
@Getter
public class ProcessXmlSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
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
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessXmlSigningCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                    String documentId, String xmlContent,
                                    String invoiceNumber, String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }
}
