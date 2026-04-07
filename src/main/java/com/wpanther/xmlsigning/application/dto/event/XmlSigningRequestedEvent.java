package com.wpanther.xmlsigning.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("invoiceDataJson")
    private final String invoiceDataJson;

    @JsonProperty("documentType")
    private final DocumentType documentType;

    /**
     * Convenience constructor for creating the event.
     *
     * @param documentId      the document ID to sign
     * @param documentNumber  the document number
     * @param xmlContent     the XML content to sign
     * @param invoiceDataJson additional invoice data as JSON
     * @param sagaId         the saga orchestration instance ID
     * @param correlationId  the end-to-end correlation ID from the originating request
     * @param documentType    the type of document
     */
    public XmlSigningRequestedEvent(String documentId, String documentNumber, String xmlContent,
                                    String invoiceDataJson, String sagaId, String correlationId,
                                    DocumentType documentType) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.documentType = documentType;
    }

    @JsonCreator
    public XmlSigningRequestedEvent(
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
        @JsonProperty("xmlContent") String xmlContent,
        @JsonProperty("invoiceDataJson") String invoiceDataJson,
        @JsonProperty("documentType") DocumentType documentType
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.documentType = documentType;
    }
}
