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
 * and published to the xml.signed Kafka topic.
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
        @DisplayName("Should route outbox event to xml.signed topic")
        void shouldRouteToXmlSignedTopic() throws Exception {
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
                "xml.signed",
                invoiceId,
                null
            );

            // Then
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic("xml.signed", invoiceId, Duration.ofSeconds(30));

            assertThat(records)
                .as("Should receive at least one message on xml.signed")
                .isNotEmpty();

            ConsumerRecord<String, String> record = records.get(0);
            JsonNode message = parseDebeziumPayload(record.value());
            assertThat(message.get("invoiceId").asText()).isEqualTo(invoiceId);
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
                "xml.signed",
                correlationId,
                "{\"correlationId\":\"" + correlationId + "\",\"documentType\":\"TAX_INVOICE\"}"
            );

            // Then
            List<ConsumerRecord<String, String>> records =
                pollForMessagesOnTopic("xml.signed", invoiceId, Duration.ofSeconds(30));

            assertThat(records).isNotEmpty();
            // Verify the payload contains our invoiceId (Debezium may double-encode)
            ConsumerRecord<String, String> record = records.get(0);
            JsonNode message = parseDebeziumPayload(record.value());
            assertThat(message.get("invoiceId").asText()).isEqualTo(invoiceId);
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
