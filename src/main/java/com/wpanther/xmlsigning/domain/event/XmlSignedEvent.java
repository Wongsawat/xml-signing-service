package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when XML document signing is completed.
 * Consumed by notification-service.
 */
@Getter
public class XmlSignedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "xml.signed";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("correlationId")
    private final String correlationId;

    public XmlSignedEvent(String invoiceId, String invoiceNumber, String documentType, String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.correlationId = correlationId;
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
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.correlationId = correlationId;
    }
}
