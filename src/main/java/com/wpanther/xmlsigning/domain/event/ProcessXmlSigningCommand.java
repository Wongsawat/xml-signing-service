package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to process XML signing.
 * Consumed from Kafka topic: saga.command.xml-signing
 */
@Getter
public class ProcessXmlSigningCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("sagaStep")
    private final String sagaStep;

    @JsonProperty("correlationId")
    private final String correlationId;

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
            @JsonProperty("sagaStep") String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version);
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessXmlSigningCommand(String sagaId, String sagaStep, String correlationId,
                                           String documentId, String xmlContent,
                                           String invoiceNumber, String documentType) {
        super();
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.xmlContent = xmlContent;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }
}
