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

    private static final String EVENT_TYPE = "XmlSignedEvent";
    private static final String SOURCE = "xml-signing-service";
    private static final String TRACE_TYPE = "XML_SIGNED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Convenience constructor for creating the event.
     *
     * @param documentId    the signed document ID
     * @param documentNumber the document number
     * @param documentType  the type of document
     * @param sagaId        the saga orchestration instance ID
     * @param correlationId the end-to-end correlation ID from the originating request
     */
    public XmlSignedEvent(String documentId, String documentNumber, String documentType,
                          String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
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
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("documentType") String documentType
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
    }
}
