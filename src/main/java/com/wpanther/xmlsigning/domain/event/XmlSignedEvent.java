package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when XML has been successfully signed
 */
@Getter
public class XmlSignedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "xml.signed";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("signedXmlContent")
    private final String signedXmlContent;

    @JsonProperty("invoiceDataJson")
    private final String invoiceDataJson;

    @JsonProperty("transactionId")
    private final String transactionId;

    @JsonProperty("certificate")
    private final String certificate;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentType")
    private final DocumentType documentType;

    public XmlSignedEvent(String documentId, String invoiceId, String invoiceNumber,
                         String signedXmlContent, String invoiceDataJson, String transactionId,
                         String certificate, String signatureLevel, String correlationId,
                         DocumentType documentType) {
        super();
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.signedXmlContent = signedXmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.correlationId = correlationId;
        this.documentType = documentType;
    }

    @JsonCreator
    public XmlSignedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("signedXmlContent") String signedXmlContent,
        @JsonProperty("invoiceDataJson") String invoiceDataJson,
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("certificate") String certificate,
        @JsonProperty("signatureLevel") String signatureLevel,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("documentType") DocumentType documentType
    ) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.signedXmlContent = signedXmlContent;
        this.invoiceDataJson = invoiceDataJson;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.correlationId = correlationId;
        this.documentType = documentType;
    }
}
