package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XML Signing Kafka Consumer Integration Tests")
class KafkaConsumerIntegrationTest extends AbstractKafkaConsumerTest {

    private static final String INPUT_TOPIC = "xml.signing.requested";

    @Test
    @DisplayName("Should sign tax invoice and write to outbox")
    void shouldSignTaxInvoiceAndWriteToOutbox() throws Exception {
        // Given
        String invoiceId = UUID.randomUUID().toString();
        String invoiceNumber = "TINV-" + invoiceId.substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = loadTestXml("tax-invoice-sample.xml");

        var event = createSigningRequestEvent(
            invoiceId, invoiceNumber, xmlContent, correlationId, DocumentType.TAX_INVOICE);

        // When
        sendEvent(INPUT_TOPIC, invoiceId, event);

        // Then - await COMPLETED status in database
        Map<String, Object> doc = awaitDocumentStatus(invoiceId, "COMPLETED");
        assertThat(doc.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(doc.get("signed_xml")).isNotNull();
        assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
        assertThat(doc.get("transaction_id")).isNotNull();

        // Verify outbox event was written with correct routing
        String documentInvoiceId = invoiceId; // EventPublisher uses invoiceId as aggregateId
        List<Map<String, Object>> outboxEvents = getOutboxEventsByAggregateId(documentInvoiceId);
        assertThat(outboxEvents).isNotEmpty();

        Map<String, Object> outboxEvent = outboxEvents.get(0);
        assertThat(outboxEvent.get("topic")).isEqualTo("xml.signed.tax-invoice");
        assertThat(outboxEvent.get("event_type")).isEqualTo("XmlSignedEvent");
        assertThat(outboxEvent.get("aggregate_type")).isEqualTo("SignedXmlDocument");

        // Verify outbox payload contains expected fields
        String payloadJson = outboxEvent.get("payload").toString();
        JsonNode payload = objectMapper.readTree(payloadJson);
        assertThat(payload.get("invoiceId").asText()).isEqualTo(invoiceId);
        assertThat(payload.get("invoiceNumber").asText()).isEqualTo(invoiceNumber);
        assertThat(payload.has("signedXmlContent")).isTrue();
        assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
    }
}
