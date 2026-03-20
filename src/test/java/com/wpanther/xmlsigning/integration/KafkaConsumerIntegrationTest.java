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

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Should sign tax invoice and write to outbox")
        void shouldSignTaxInvoiceAndWriteToOutbox() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String invoiceNumber = "TINV-" + invoiceId.substring(0, 8);
            String correlationId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("tax-invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, invoiceNumber, xmlContent, invoiceId, correlationId, DocumentType.TAX_INVOICE);

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
            assertThat(outboxEvent.get("topic")).isEqualTo("xml.signed");
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

        @Test
        @DisplayName("Should sign invoice and write to outbox")
        void shouldSignInvoiceAndWriteToOutbox() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String invoiceNumber = "INV-" + invoiceId.substring(0, 8);
            String correlationId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, invoiceNumber, xmlContent, invoiceId, correlationId, DocumentType.INVOICE);

            // When
            sendEvent(INPUT_TOPIC, invoiceId, event);

            // Then
            Map<String, Object> doc = awaitDocumentStatus(invoiceId, "COMPLETED");
            assertThat(doc.get("document_type")).isEqualTo("INVOICE");

            List<Map<String, Object>> outboxEvents = getOutboxEventsByAggregateId(invoiceId);
            assertThat(outboxEvents).isNotEmpty();
            assertThat(outboxEvents.get(0).get("topic")).isEqualTo("xml.signed");
        }

        @Test
        @DisplayName("Should detect document type from XML namespace when not provided in event")
        void shouldDetectDocumentTypeFromXmlContent() throws Exception {
            // Given - no documentType in event, should detect from XML namespace
            String invoiceId = UUID.randomUUID().toString();
            String invoiceNumber = "TINV-DETECT-" + invoiceId.substring(0, 8);
            String correlationId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("tax-invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, invoiceNumber, xmlContent, invoiceId, correlationId, null);

            // When
            sendEvent(INPUT_TOPIC, invoiceId, event);

            // Then - should detect TAX_INVOICE from namespace URI
            Map<String, Object> doc = awaitDocumentStatus(invoiceId, "COMPLETED");
            assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
        }
    }

    @Nested
    @DisplayName("Duplicate Handling")
    class DuplicateHandling {

        @Test
        @DisplayName("Should skip already signed document on duplicate event")
        void shouldSkipAlreadySignedDocument() throws Exception {
            // Given - sign a document first
            String invoiceId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("tax-invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, "TINV-DUP-001", xmlContent, invoiceId, UUID.randomUUID().toString(), DocumentType.TAX_INVOICE);

            sendEvent(INPUT_TOPIC, invoiceId, event);
            awaitDocumentStatus(invoiceId, "COMPLETED");

            // When - send duplicate
            sendEvent(INPUT_TOPIC, invoiceId, event);

            // Then - wait a bit and verify only one record exists
            Thread.sleep(5000);
            Integer count = testJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM signed_xml_documents WHERE invoice_id = ?",
                Integer.class, invoiceId);
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle signing failure and set FAILED status")
        void shouldHandleSigningFailureAndSetFailedStatus() throws Exception {
            // Given - configure mock to throw exception
            setupSigningFailure("CSC API connection refused");

            String invoiceId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("tax-invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, "TINV-FAIL-001", xmlContent, invoiceId, UUID.randomUUID().toString(), DocumentType.TAX_INVOICE);

            // When
            sendEvent(INPUT_TOPIC, invoiceId, event);

            // Then
            Map<String, Object> doc = awaitDocumentStatus(invoiceId, "FAILED");
            assertThat(doc.get("error_message")).asString().isNotEmpty();
            assertThat(doc.get("signed_xml")).isNull();
        }
    }

    @Nested
    @DisplayName("Outbox Verification")
    class OutboxVerification {

        @Test
        @DisplayName("Should write correct outbox payload with all XmlSignedEvent fields")
        void shouldWriteCorrectOutboxPayload() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String invoiceNumber = "TINV-PAYLOAD-" + invoiceId.substring(0, 8);
            String correlationId = UUID.randomUUID().toString();
            String xmlContent = loadTestXml("tax-invoice-sample.xml");

            var event = createSigningRequestEvent(
                invoiceId, invoiceNumber, xmlContent, invoiceId, correlationId, DocumentType.TAX_INVOICE);

            // When
            sendEvent(INPUT_TOPIC, invoiceId, event);

            // Then
            awaitDocumentStatus(invoiceId, "COMPLETED");
            List<Map<String, Object>> outboxEvents = getOutboxEventsByAggregateId(invoiceId);
            assertThat(outboxEvents).hasSize(1);

            Map<String, Object> outboxEvent = outboxEvents.get(0);

            // Verify outbox metadata
            assertThat(outboxEvent.get("aggregate_type")).isEqualTo("SignedXmlDocument");
            assertThat(outboxEvent.get("aggregate_id")).isEqualTo(invoiceId);
            assertThat(outboxEvent.get("event_type")).isEqualTo("XmlSignedEvent");
            assertThat(outboxEvent.get("topic")).isEqualTo("xml.signed");
            assertThat(outboxEvent.get("partition_key")).isEqualTo(correlationId);

            // Verify outbox payload JSON
            String payloadJson = outboxEvent.get("payload").toString();
            JsonNode payload = objectMapper.readTree(payloadJson);

            assertThat(payload.get("invoiceId").asText()).isEqualTo(invoiceId);
            assertThat(payload.get("invoiceNumber").asText()).isEqualTo(invoiceNumber);
            assertThat(payload.has("signedXmlContent")).isTrue();
            assertThat(payload.get("signedXmlContent").asText()).isNotBlank();
            assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
            assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
            assertThat(payload.has("transactionId")).isTrue();
            assertThat(payload.has("signatureLevel")).isTrue();

            // Verify headers contain correlationId and documentType
            String headersJson = (String) outboxEvent.get("headers");
            if (headersJson != null) {
                JsonNode headers = objectMapper.readTree(headersJson);
                assertThat(headers.get("correlationId").asText()).isEqualTo(correlationId);
                assertThat(headers.get("documentType").asText()).isEqualTo("TAX_INVOICE");
            }
        }
    }
}
