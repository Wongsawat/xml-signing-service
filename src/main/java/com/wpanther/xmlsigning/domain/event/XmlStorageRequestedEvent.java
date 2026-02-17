package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a signed XML document needs to be stored.
 * Consumed by document-storage-service.
 */
@Getter
public class XmlStorageRequestedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "XmlStorageRequestedEvent";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Convenience constructor for creating events.
     */
    public XmlStorageRequestedEvent(String invoiceId, String xmlContent,
                                   String documentType, String invoiceNumber, String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.xmlContent = xmlContent;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    /**
     * JsonCreator for deserialization.
     */
    @JsonCreator
    public XmlStorageRequestedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("invoiceId") String invoiceId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("correlationId") String correlationId) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.xmlContent = xmlContent;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.correlationId = correlationId;
    }
}
