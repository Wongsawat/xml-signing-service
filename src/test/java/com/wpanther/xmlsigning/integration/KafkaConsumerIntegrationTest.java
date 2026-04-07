package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka consumer integration tests for saga command consumption.
 * <p>
 * Tests that the xml-signing-service correctly consumes saga commands from
 * {@code saga.command.xml-signing} and {@code saga.compensation.xml-signing},
 * processes XML signing via mocked CSC API, persists to PostgreSQL, and writes
 * outbox events for Debezium CDC publishing.
 * <p>
 * Requires external containers: PostgreSQL:5433, Kafka:9093
 * Start with: {@code cd ../../scripts && ./test-containers-start.sh}
 */
@DisplayName("XML Signing Saga Command Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class KafkaConsumerIntegrationTest extends AbstractKafkaConsumerTest {

    private static final String SAGA_COMMAND_TOPIC = "saga.command.xml-signing";
    private static final String SAGA_COMPENSATION_TOPIC = "saga.compensation.xml-signing";

    // ========== Happy Path Tests ==========

    @Test
    @DisplayName("Should process valid tax invoice saga command end-to-end")
    void shouldProcessValidTaxInvoiceSagaCommandEndToEnd() throws Exception {
        // Given — unique identifiers per test run
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "TINV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, invoiceNumber, xmlContent, correlationId, "TAX_INVOICE");

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — await COMPLETED status in database
        Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");
        assertThat(doc.get("invoice_id")).isEqualTo(documentId);
        assertThat(doc.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(doc.get("status")).isEqualTo("COMPLETED");
        assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
        assertThat(doc.get("transaction_id")).isNotNull();
        assertThat(doc.get("signed_xml_url")).isNotNull();
        assertThat(doc.get("signed_xml_size")).isNotNull();

        // Verify xml.signed outbox event (aggregate_id = documentId)
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(documentId, 1);
        List<Map<String, Object>> xmlSignedOutbox = getOutboxEventsByAggregateId(documentId);
        assertThat(xmlSignedOutbox).hasSize(1);

        Map<String, Object> xmlSignedEvent = xmlSignedOutbox.get(0);
        assertThat(xmlSignedEvent.get("topic")).isEqualTo("xml.signed");
        assertThat(xmlSignedEvent.get("event_type")).isEqualTo("XmlSignedEvent");
        assertThat(xmlSignedEvent.get("aggregate_type")).isEqualTo("SignedXmlDocument");

        // Verify saga.reply.xml-signing outbox event (aggregate_id = sagaId)
        awaitOutboxEventCount(sagaId, 1);
        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(sagaId);
        assertThat(replyOutbox).isNotEmpty();

        Map<String, Object> replyEvent = replyOutbox.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event"));
        String replyPayload = (String) replyEvent.get("payload");
        assertThat(replyPayload).contains("SUCCESS");
        assertThat(replyPayload).contains(correlationId);
    }

    @Test
    @DisplayName("Should sign invoice document type via saga command")
    void shouldSignInvoiceDocumentType() throws Exception {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleInvoiceXml();

        var command = createProcessCommand(
            documentId, invoiceNumber, xmlContent, correlationId, "INVOICE");

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then
        Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");
        assertThat(doc.get("document_type")).isEqualTo("INVOICE");

        // Verify outbox events
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(sagaId, 1);
        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(sagaId);
        assertThat(replyOutbox).isNotEmpty();
        assertThat((String) replyOutbox.get(0).get("payload")).contains("SUCCESS");
    }

    @Test
    @DisplayName("Should detect TAX_INVOICE document type from XML namespace when documentType is null")
    void shouldDetectDocumentTypeFromXmlNamespace() throws Exception {
        // Given — no documentType in command, should detect from XML namespace
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "TINV-DETECT-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, invoiceNumber, xmlContent, correlationId, null);

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — should detect TAX_INVOICE from namespace URI
        Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");
        assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
    }

    // ========== Outbox Event Verification ==========

    @Test
    @DisplayName("Should write correct outbox payloads with all XmlSignedEvent fields")
    void shouldWriteCorrectOutboxPayloads() throws Exception {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "TINV-OUTBOX-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, invoiceNumber, xmlContent, correlationId, "TAX_INVOICE");

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — verify xml.signed outbox event
        awaitDocumentStatus(documentId, "COMPLETED");
        awaitOutboxEventCount(documentId, 1);

        List<Map<String, Object>> outboxEvents = getOutboxEventsByAggregateId(documentId);
        Map<String, Object> xmlSignedEvent = outboxEvents.get(0);

        // Verify outbox metadata
        assertThat(xmlSignedEvent.get("aggregate_type")).isEqualTo("SignedXmlDocument");
        assertThat(xmlSignedEvent.get("aggregate_id")).isEqualTo(documentId);
        assertThat(xmlSignedEvent.get("event_type")).isEqualTo("XmlSignedEvent");
        assertThat(xmlSignedEvent.get("topic")).isEqualTo("xml.signed");

        // Verify outbox payload JSON
        String payloadJson = xmlSignedEvent.get("payload").toString();
        JsonNode payload = objectMapper.readTree(payloadJson);

        assertThat(payload.get("invoiceId").asText()).isEqualTo(documentId);
        assertThat(payload.get("invoiceNumber").asText()).isEqualTo(invoiceNumber);
        assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
        assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);

        // Verify headers contain correlationId and invoiceNumber
        String headersJson = (String) xmlSignedEvent.get("headers");
        if (headersJson != null) {
            JsonNode headers = objectMapper.readTree(headersJson);
            assertThat(headers.get("correlationId").asText()).isEqualTo(correlationId);
            assertThat(headers.get("invoiceNumber").asText()).isEqualTo(invoiceNumber);
        }
    }

    @Test
    @DisplayName("Should write saga reply with SUCCESS status to outbox")
    void shouldWriteSagaReplySuccessToOutbox() throws Exception {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "TINV-REPLY-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, invoiceNumber, xmlContent, correlationId, "TAX_INVOICE");

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — verify saga reply outbox event
        awaitDocumentStatus(documentId, "COMPLETED");
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(sagaId, 1);

        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(sagaId);
        Map<String, Object> replyEvent = replyOutbox.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event"));

        assertThat(replyEvent.get("aggregate_type")).isEqualTo("SignedXmlDocument");
        assertThat(replyEvent.get("aggregate_id")).isEqualTo(sagaId);
        assertThat(replyEvent.get("partition_key")).isEqualTo(sagaId);

        String replyPayload = (String) replyEvent.get("payload");
        assertThat(replyPayload).contains("SUCCESS");
        assertThat(replyPayload).contains(correlationId);
        assertThat(replyPayload).contains(sagaId);

        // Verify reply headers
        String headersJson = (String) replyEvent.get("headers");
        if (headersJson != null) {
            JsonNode headers = objectMapper.readTree(headersJson);
            assertThat(headers.get("sagaId").asText()).isEqualTo(sagaId);
            assertThat(headers.get("correlationId").asText()).isEqualTo(correlationId);
            assertThat(headers.get("status").asText()).isEqualTo("SUCCESS");
        }
    }

    // ========== Duplicate Handling ==========

    @Test
    @DisplayName("Should skip already signed document on duplicate command and still reply SUCCESS")
    void shouldSkipAlreadySignedDocument() throws Exception {
        // Given — sign a document first
        String documentId = "DOC-" + UUID.randomUUID();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, "TINV-DUP-001", xmlContent, UUID.randomUUID().toString(), "TAX_INVOICE");

        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);
        awaitDocumentStatus(documentId, "COMPLETED");

        // When — send duplicate with new correlation ID
        String newCorrelationId = UUID.randomUUID().toString();
        var duplicateCommand = createProcessCommand(
            documentId, "TINV-DUP-001", xmlContent, newCorrelationId, "TAX_INVOICE");
        sendEvent(SAGA_COMMAND_TOPIC, documentId, duplicateCommand);

        // Then — only 1 record exists
        Thread.sleep(5000);
        Integer count = testJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM signed_xml_documents WHERE invoice_id = ?",
            Integer.class, documentId);
        assertThat(count).isEqualTo(1);

        // The duplicate should still produce a SUCCESS reply for the new saga
        String newSagaId = "saga-" + newCorrelationId;
        awaitOutboxEventCount(newSagaId, 1);
        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(newSagaId);
        assertThat(replyOutbox).isNotEmpty();
        assertThat((String) replyOutbox.get(0).get("payload")).contains("SUCCESS");
    }

    // ========== Error Handling ==========

    @Test
    @DisplayName("Should handle signing failure and set FAILED status with FAILURE reply")
    void shouldHandleSigningFailureWithFailureReply() throws Exception {
        // Given — configure mock to throw exception
        setupSigningFailure("CSC API connection refused");

        String documentId = "DOC-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = getSampleTaxInvoiceXml();

        var command = createProcessCommand(
            documentId, "TINV-FAIL-001", xmlContent, correlationId, "TAX_INVOICE");

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — document should be FAILED
        Map<String, Object> doc = awaitDocumentStatus(documentId, "FAILED");
        assertThat(doc.get("error_message")).asString().isNotEmpty();

        // FAILURE reply written to outbox
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(sagaId, 1);
        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(sagaId);
        Map<String, Object> replyEvent = replyOutbox.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event"));
        assertThat((String) replyEvent.get("payload")).contains("FAILURE");
    }

    @Test
    @DisplayName("Should send FAILURE reply for invalid XML that cannot detect document type")
    void shouldSendFailureReplyForInvalidXml() throws Exception {
        // Given — XML content that won't match any known document type
        String documentId = "DOC-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String xmlContent = "<root><invalid>This is not a valid e-Tax document format whatsoever</invalid></root>";

        var command = createProcessCommand(
            documentId, "TINV-INVALID", xmlContent, correlationId, null);

        // When
        sendEvent(SAGA_COMMAND_TOPIC, documentId, command);

        // Then — no document created
        assertNoDocumentCreatedAfterWait(documentId);

        // But FAILURE reply should be written to outbox
        String sagaId = "saga-" + correlationId;
        awaitOutboxEventCount(sagaId, 1);
        List<Map<String, Object>> replyOutbox = getOutboxEventsByAggregateId(sagaId);
        Map<String, Object> replyEvent = replyOutbox.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event"));
        assertThat((String) replyEvent.get("payload")).contains("FAILURE");
    }

    // ========== Multiple Documents ==========

    @Test
    @DisplayName("Should process multiple saga commands with different document IDs")
    void shouldProcessMultipleDocuments() throws Exception {
        // Given
        String documentId1 = "DOC-" + UUID.randomUUID();
        String documentId2 = "DOC-" + UUID.randomUUID();
        String invoiceNumber1 = "TINV-M1-" + UUID.randomUUID().toString().substring(0, 8);
        String invoiceNumber2 = "TINV-M2-" + UUID.randomUUID().toString().substring(0, 8);
        String correlationId1 = UUID.randomUUID().toString();
        String correlationId2 = UUID.randomUUID().toString();

        var command1 = createProcessCommand(
            documentId1, invoiceNumber1, getSampleTaxInvoiceXml(), correlationId1, "TAX_INVOICE");
        var command2 = createProcessCommand(
            documentId2, invoiceNumber2, getSampleInvoiceXml(), correlationId2, "INVOICE");

        // When — send both commands
        sendEvent(SAGA_COMMAND_TOPIC, documentId1, command1);
        sendEvent(SAGA_COMMAND_TOPIC, documentId2, command2);

        // Then — both should be processed
        Map<String, Object> doc1 = awaitDocumentStatus(documentId1, "COMPLETED");
        Map<String, Object> doc2 = awaitDocumentStatus(documentId2, "COMPLETED");

        assertThat(doc1.get("document_type")).isEqualTo("TAX_INVOICE");
        assertThat(doc2.get("document_type")).isEqualTo("INVOICE");

        assertThat(getDocumentCount()).isGreaterThanOrEqualTo(2);
    }

    // ========== Compensation Tests ==========

    @Test
    @DisplayName("Should compensate (delete) a previously signed document")
    void shouldCompensateSignedDocument() throws Exception {
        // Given — process a document first
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "TINV-COMP-" + UUID.randomUUID().toString().substring(0, 8);
        String processCorrelationId = UUID.randomUUID().toString();

        var processCommand = createProcessCommand(
            documentId, invoiceNumber, getSampleTaxInvoiceXml(), processCorrelationId, "TAX_INVOICE");
        sendEvent(SAGA_COMMAND_TOPIC, documentId, processCommand);
        awaitDocumentStatus(documentId, "COMPLETED");

        // When — send compensation command
        String compensateCorrelationId = UUID.randomUUID().toString();
        String compensateSagaId = "saga-" + compensateCorrelationId;
        var compensateCommand = createCompensateCommand(documentId, compensateCorrelationId);
        sendEvent(SAGA_COMPENSATION_TOPIC, documentId, compensateCommand);

        // Then — document deleted from DB
        await().atMost(2, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getDocumentByInvoiceId(documentId) == null);

        // COMPENSATED reply written to outbox
        await().atMost(2, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS)
               .until(() -> !getOutboxEventsByAggregateId(compensateSagaId).isEmpty());

        List<Map<String, Object>> sagaOutboxEvents = getOutboxEventsByAggregateId(compensateSagaId);
        Map<String, Object> replyEvent = sagaOutboxEvents.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event after compensation"));
        assertThat((String) replyEvent.get("payload")).contains("COMPENSATED");
        assertThat((String) replyEvent.get("payload")).contains(compensateCorrelationId);
    }

    @Test
    @DisplayName("Should send COMPENSATED reply even for non-existent document (idempotent)")
    void shouldSendCompensatedReplyForNonExistentDocument() throws Exception {
        // Given — document was never processed
        String documentId = "DOC-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String sagaId = "saga-" + correlationId;

        var compensateCommand = createCompensateCommand(documentId, correlationId);

        // When
        sendEvent(SAGA_COMPENSATION_TOPIC, documentId, compensateCommand);

        // Then — COMPENSATED reply still written to outbox
        await().atMost(2, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS)
               .until(() -> !getOutboxEventsByAggregateId(sagaId).isEmpty());

        List<Map<String, Object>> sagaOutboxEvents = getOutboxEventsByAggregateId(sagaId);
        Map<String, Object> replyEvent = sagaOutboxEvents.stream()
            .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
            .findFirst().orElseThrow(() -> new AssertionError("No saga.reply.xml-signing outbox event"));
        assertThat((String) replyEvent.get("payload")).contains("COMPENSATED");
    }
}
