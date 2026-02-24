package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to request XML signing.
 */
@Getter
public class XmlSigningRequestedEvent extends TraceEvent {

    private static final String SOURCE = "xml-signing-service";
    private static final String TRACE_TYPE = "XML_SIGNING_REQUESTED";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("invoiceDataJson")
    private final String invoiceDataJson;

    @JsonProperty("documentType")
    private final DocumentType documentType;

    /**
     * Convenience constructor. correlationId is stored as sagaId in TraceEvent.
     */
    public XmlSigningRequestedEvent(String invoiceId, String invoiceNumber, String xmlContent,
                                    String invoiceDataJson, String correlationId, DocumentType documentType) {
        super(correlationId, SOURCE, TRACE_TYPE, null);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.documentType = documentType;
    }

    /**
     * Returns the correlation ID (stored as sagaId in TraceEvent).
     */
    @JsonIgnore
    public String getCorrelationId() {
        return getSagaId();
    }

    @JsonCreator
    public XmlSigningRequestedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("xmlContent") String xmlContent,
        @JsonProperty("invoiceDataJson") String invoiceDataJson,
        @JsonProperty("documentType") DocumentType documentType
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.documentType = documentType;
    }
}
