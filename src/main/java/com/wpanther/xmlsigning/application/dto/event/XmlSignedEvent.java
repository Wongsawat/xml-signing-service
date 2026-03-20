package com.wpanther.xmlsigning.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when XML document signing is completed.
 * Consumed by notification-service.
 */
@Getter
public class XmlSignedEvent extends TraceEvent {

    private static final String EVENT_TYPE = "xml.signed";
    private static final String SOURCE = "xml-signing-service";
    private static final String TRACE_TYPE = "XML_SIGNED";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Convenience constructor for creating the event.
     *
     * @param invoiceId     the signed document ID
     * @param invoiceNumber the invoice number
     * @param documentType  the type of document
     * @param sagaId        the saga orchestration instance ID
     * @param correlationId the end-to-end correlation ID from the originating request
     */
    public XmlSignedEvent(String invoiceId, String invoiceNumber, String documentType,
                          String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public XmlSignedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
    }
}
