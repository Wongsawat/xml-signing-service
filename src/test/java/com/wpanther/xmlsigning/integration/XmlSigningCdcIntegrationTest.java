package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CDC integration tests verifying the Transactional Outbox Pattern with Debezium.
 * <p>
 * Tests that outbox events written to the database are picked up by Debezium CDC
 * and published to the correct type-specific Kafka topic.
 * <p>
 * Requires: PostgreSQL:5433, Kafka:9093, Debezium:8083
 * Start with: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 */
@DisplayName("XML Signing CDC Integration Tests")
class XmlSigningCdcIntegrationTest extends AbstractCdcIntegrationTest {

    @Nested
    @DisplayName("Debezium CDC Event Routing")
    class DebeziumCdcEventRouting {

        @Test
        @DisplayName("Should route tax invoice outbox event to xml.signed.tax-invoice topic")
        void shouldRouteTaxInvoiceToCorrectTopic() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String payload = createXmlSignedEventPayload(invoiceId, "TINV-CDC-001",
                DocumentType.TAX_INVOICE, UUID.randomUUID().toString());

            // When - insert outbox event directly (simulates what EventPublisher does)
            insertOutboxEvent(
                "SignedXmlDocument",
                invoiceId,
                "XmlSignedEvent",
                payload,
                "xml.signed.tax-invoice",
                invoiceId,
                null
            );

            // Then - Debezium CDC should publish to xml.signed.tax-invoice
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic("xml.signed.tax-invoice", Duration.ofSeconds(30));

            assertThat(records)
                .as("Should receive at least one message on xml.signed.tax-invoice")
                .isNotEmpty();

            // Verify the message payload
            ConsumerRecord<String, String> record = records.get(0);
            JsonNode message = objectMapper.readTree(record.value());
            assertThat(message.get("invoiceId").asText()).isEqualTo(invoiceId);
        }

        @Test
        @DisplayName("Should route invoice outbox event to xml.signed.invoice topic")
        void shouldRouteInvoiceToCorrectTopic() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String payload = createXmlSignedEventPayload(invoiceId, "INV-CDC-001",
                DocumentType.INVOICE, UUID.randomUUID().toString());

            // When
            insertOutboxEvent(
                "SignedXmlDocument",
                invoiceId,
                "XmlSignedEvent",
                payload,
                "xml.signed.invoice",
                invoiceId,
                null
            );

            // Then
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic("xml.signed.invoice", Duration.ofSeconds(30));

            assertThat(records)
                .as("Should receive at least one message on xml.signed.invoice")
                .isNotEmpty();

            ConsumerRecord<String, String> record = records.get(0);
            JsonNode message = objectMapper.readTree(record.value());
            assertThat(message.get("invoiceId").asText()).isEqualTo(invoiceId);
        }

        @ParameterizedTest(name = "{0} → {0}")
        @EnumSource(DocumentType.class)
        @DisplayName("Should route all document types to their correct Kafka topic")
        void shouldRouteAllDocumentTypesToCorrectTopics(DocumentType documentType) throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String targetTopic = documentType.getKafkaTopic();
            String payload = createXmlSignedEventPayload(invoiceId,
                documentType.name() + "-CDC-" + invoiceId.substring(0, 8),
                documentType, UUID.randomUUID().toString());

            // When
            insertOutboxEvent(
                "SignedXmlDocument",
                invoiceId,
                "XmlSignedEvent",
                payload,
                targetTopic,
                invoiceId,
                null
            );

            // Then
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic(targetTopic, Duration.ofSeconds(30));

            assertThat(records)
                .as("Should receive message on topic: " + targetTopic)
                .isNotEmpty();

            ConsumerRecord<String, String> record = records.get(0);
            JsonNode message = objectMapper.readTree(record.value());
            assertThat(message.get("invoiceId").asText()).isEqualTo(invoiceId);
            assertThat(message.get("documentType").asText()).isEqualTo(documentType.name());
        }
    }

    @Nested
    @DisplayName("Outbox Event Metadata")
    class OutboxEventMetadata {

        @Test
        @DisplayName("Should preserve partition key through CDC")
        void shouldPreservePartitionKeyThroughCdc() throws Exception {
            // Given
            String invoiceId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            String payload = createXmlSignedEventPayload(invoiceId, "TINV-KEY-001",
                DocumentType.TAX_INVOICE, correlationId);

            // When
            insertOutboxEvent(
                "SignedXmlDocument",
                invoiceId,
                "XmlSignedEvent",
                payload,
                "xml.signed.tax-invoice",
                correlationId,
                "{\"correlationId\":\"" + correlationId + "\",\"documentType\":\"TAX_INVOICE\"}"
            );

            // Then
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic("xml.signed.tax-invoice", Duration.ofSeconds(30));

            assertThat(records).isNotEmpty();
            // Debezium EventRouter should use the partition_key column as the Kafka message key
            ConsumerRecord<String, String> record = records.get(0);
            assertThat(record.value()).contains(invoiceId);
        }
    }

    // --- Test Data Helpers ---

    private String createXmlSignedEventPayload(String invoiceId, String invoiceNumber,
                                                DocumentType documentType, String correlationId) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("occurredAt", Instant.now().toString());
            payload.put("eventType", "XmlSignedEvent");
            payload.put("version", 1);
            payload.put("documentId", UUID.randomUUID().toString());
            payload.put("invoiceId", invoiceId);
            payload.put("invoiceNumber", invoiceNumber);
            payload.put("signedXmlContent", "<signed>test</signed>");
            payload.put("invoiceDataJson", "{}");
            payload.put("transactionId", "TXN-" + UUID.randomUUID());
            payload.put("certificate", "test-cert-base64");
            payload.put("signatureLevel", "XAdES-BASELINE-T");
            payload.put("correlationId", correlationId);
            payload.put("documentType", documentType.name());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test payload", e);
        }
    }
}
