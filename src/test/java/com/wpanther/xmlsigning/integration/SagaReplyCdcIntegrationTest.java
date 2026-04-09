package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CDC integration tests verifying saga.reply.xml-signing events reach Kafka via Debezium.
 *
 * <p>These tests exercise the <strong>complete pipeline</strong> with no mocks:
 * <ol>
 *   <li>Send real saga.command.xml-signing with valid XML</li>
 *   <li>Service signs via real CSC API (eidasremotesigning)</li>
 *   <li>Service writes outbox events to PostgreSQL</li>
 *   <li>Debezium CDC reads outbox and publishes to Kafka</li>
 *   <li>Test consumer polls saga.reply.xml-signing topic and verifies content</li>
 * </ol>
 *
 * <p><strong>Start required containers before running:</strong>
 * <pre>
 *   cd invoice-microservices
 *   ./scripts/test-containers-clean.sh
 *   ./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
 * </pre>
 *
 * <p><strong>Run command:</strong>
 * <pre>
 *   cd services/xml-signing-service
 *   mvn clean test -Pintegration -Dtest="SagaReplyCdcIntegrationTest"
 * </pre>
 */
@DisplayName("CDC Integration: saga.reply.xml-signing via Debezium")
@Tag("full-integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class SagaReplyCdcIntegrationTest extends AbstractFullIntegrationTest {

    private static final String COMMAND_TOPIC = "saga.command.xml-signing";
    private static final String COMPENSATION_TOPIC = "saga.compensation.xml-signing";

    // =========================================================================
    // Success reply: TaxInvoice
    // =========================================================================

    @Nested
    @DisplayName("SUCCESS reply via CDC — TaxInvoice")
    class SuccessReplyTaxInvoice {

        @Test
        @DisplayName("Should publish SUCCESS reply to saga.reply.xml-signing Kafka topic via CDC")
        void shouldPublishSuccessReplyToKafkaViaCdc() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-CDC-001",
                    getTaxInvoice2p1ValidXml(), correlationId, "TAX_INVOICE");

            // Act — send command, wait for service to process
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — poll saga.reply.xml-signing Kafka topic for CDC message
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(sagaId, Duration.ofSeconds(60));

            assertThat(records)
                    .as("Debezium CDC should deliver saga reply to saga.reply.xml-signing topic")
                    .isNotEmpty();

            // Verify the payload contains our sagaId
            ConsumerRecord<String, String> record = records.get(0);
            JsonNode payload = parseDebeziumPayload(record.value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
        }

        @Test
        @DisplayName("Should contain correct fields in SUCCESS reply payload on Kafka")
        void shouldContainCorrectFieldsInSuccessReply() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String documentNumber = "TINV-CDC-FIELDS-001";
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, documentNumber,
                    getTaxInvoice2p1ValidXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — inspect full Kafka message content
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(correlationId, Duration.ofSeconds(60));
            assertThat(records).isNotEmpty();

            JsonNode payload = parseDebeziumPayload(records.get(0).value());

            // Core reply fields
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);
            assertThat(payload.get("status").asText()).isEqualTo("SUCCESS");
            assertThat(payload.get("sagaStep").asText()).isEqualTo("sign-xml");
            assertThat(payload.get("eventType").asText()).isEqualTo("XmlSigningReplyEvent");
            assertThat(payload.get("version").asInt()).isEqualTo(1);

            // Event metadata fields
            assertThat(payload.has("eventId")).isTrue();
            assertThat(payload.has("occurredAt")).isTrue();

            // Success-specific fields
            assertThat(payload.get("signedXmlUrl").asText()).isNotBlank();
            assertThat(payload.get("signedXmlSize").asLong()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should include signedXmlUrl pointing to MinIO in Kafka reply")
        void shouldContainSignedXmlUrlPointingToMinIO() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-CDC-URL-001",
                    getTaxInvoice2p1ValidXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — signedXmlUrl in Kafka message
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(sagaId, Duration.ofSeconds(60));
            assertThat(records).isNotEmpty();

            JsonNode payload = parseDebeziumPayload(records.get(0).value());
            String signedXmlUrl = payload.get("signedXmlUrl").asText();
            assertThat(signedXmlUrl).contains("signed-xml-documents");
            assertThat(signedXmlUrl).contains("TAX_INVOICE");
        }

        @Test
        @DisplayName("Should preserve sagaId as Kafka message key through CDC")
        void shouldPreserveSagaIdAsKafkaMessageKey() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-CDC-KEY-001",
                    getTaxInvoice2p1ValidXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — Kafka message key matches sagaId (partition key)
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(sagaId, Duration.ofSeconds(60));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> record = records.get(0);
            assertThat(record.key())
                    .as("Kafka message key should be the sagaId used as partition_key")
                    .isEqualTo(sagaId);
        }
    }

    // =========================================================================
    // Success reply: Invoice
    // =========================================================================

    @Nested
    @DisplayName("SUCCESS reply via CDC — Invoice")
    class SuccessReplyInvoice {

        @Test
        @DisplayName("Should publish SUCCESS reply to Kafka for Invoice document type")
        void shouldPublishSuccessReplyForInvoiceDocumentType() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "INV-CDC-001",
                    getInvoice2p1ValidXml(), correlationId, "INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — CDC message on saga.reply.xml-signing
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(sagaId, Duration.ofSeconds(60));
            assertThat(records).isNotEmpty();

            JsonNode payload = parseDebeziumPayload(records.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(payload.get("status").asText()).isEqualTo("SUCCESS");

            // signedXmlUrl should reference INVOICE type
            String signedXmlUrl = payload.get("signedXmlUrl").asText();
            assertThat(signedXmlUrl).contains("INVOICE");
        }
    }

    // =========================================================================
    // Compensation reply via CDC
    // =========================================================================

    @Nested
    @DisplayName("COMPENSATED reply via CDC")
    class CompensatedReply {

        @Test
        @DisplayName("Should publish COMPENSATED reply to Kafka after compensation command")
        void shouldPublishCompensatedReplyToKafka() throws Exception {
            // Arrange — sign a document first
            String documentId = newDocumentId();
            String processCorrelationId = newCorrelationId();

            var processCommand = createProcessCommand(
                    documentId, "TINV-CDC-COMP-001",
                    getTaxInvoice2p1ValidXml(), processCorrelationId, "TAX_INVOICE");
            sendEvent(COMMAND_TOPIC, documentId, processCommand);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Act — compensate
            String compensateCorrelationId = newCorrelationId();
            String compensateSagaId = sagaIdFor(compensateCorrelationId);

            var compensateCommand = createCompensateCommand(documentId, compensateCorrelationId);
            sendEvent(COMPENSATION_TOPIC, documentId, compensateCommand);
            awaitDocumentDeleted(documentId);

            // Assert — COMPENSATED reply on Kafka via CDC
            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(compensateSagaId, Duration.ofSeconds(60));
            assertThat(records)
                    .as("Debezium CDC should deliver COMPENSATED reply to Kafka")
                    .isNotEmpty();

            JsonNode payload = parseDebeziumPayload(records.get(0).value());
            assertThat(payload.get("sagaId").asText()).isEqualTo(compensateSagaId);
            assertThat(payload.get("correlationId").asText()).isEqualTo(compensateCorrelationId);
            assertThat(payload.get("status").asText()).isEqualTo("COMPENSATED");
            assertThat(payload.get("sagaStep").asText()).isEqualTo("sign-xml");
        }
    }

    // =========================================================================
    // Debezium payload encoding

    @Nested
    @DisplayName("Debezium payload encoding")
    class DebeziumPayloadEncoding {

        @Test
        @DisplayName("Should correctly unwrap Debezium double-encoded payload for saga reply")
        void shouldHandleDoubleEncodedPayload() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-CDC-ENCODE-001",
                    getTaxInvoice2p1ValidXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            List<ConsumerRecord<String, String>> records =
                    pollSagaReplyFromKafka(sagaId, Duration.ofSeconds(60));
            assertThat(records).isNotEmpty();

            // Assert — raw value from Kafka
            String rawValue = records.get(0).value();

            // Raw Kafka value should be parseable as JSON (may be double-encoded or CDC envelope)
            JsonNode rawNode = objectMapper.readTree(rawValue);
            assertThat(rawNode.isValueNode() || rawNode.isObject() || rawNode.isArray())
                    .as("Raw Kafka value should be valid JSON")
                    .isTrue();

            // parseDebeziumPayload extracts the actual event from Debezium envelope
            JsonNode payload = parseDebeziumPayload(rawValue);

            // Verify the unwrapped payload has expected fields
            assertThat(payload.isObject()).isTrue();
            assertThat(payload.has("sagaId")).isTrue();
            assertThat(payload.has("status")).isTrue();
            assertThat(payload.has("correlationId")).isTrue();
            assertThat(payload.get("sagaId").asText()).isEqualTo(sagaId);
        }
    }
}
