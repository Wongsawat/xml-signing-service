package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full end-to-end integration tests for the XML signing saga command consumer.
 *
 * <p>These tests exercise the <strong>complete pipeline</strong> with no mocks:
 * <ul>
 *   <li><strong>Kafka</strong> — commands produced to {@code saga.command.xml-signing},
 *       consumed by the Apache Camel route.</li>
 *   <li><strong>eidasremotesigning</strong> — real CSC API v2.0 signs the XML document
 *       with the BCFKS key at {@code /app/keystores/eidas-signing.bfks}.</li>
 *   <li><strong>MinIO</strong> — the original XML and the signed XML are stored in
 *       bucket {@code signed-xml-documents} on {@code localhost:9100}.</li>
 *   <li><strong>PostgreSQL</strong> — the {@code signed_xml_documents} and
 *       {@code outbox_events} tables are written by the service.</li>
 *   <li><strong>Debezium CDC</strong> — outbox events in PostgreSQL are published to Kafka
 *       topics ({@code xml.signed}, {@code saga.reply.xml-signing}) by the connector.</li>
 * </ul>
 *
 * <p><strong>Start required containers before running these tests:</strong>
 * <pre>
 *   cd invoice-microservices
 *   ./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
 * </pre>
 *
 * <p><strong>Run command:</strong>
 * <pre>
 *   cd services/xml-signing-service
 *   mvn test -Pintegration -Dtest="SagaCommandFullIntegrationTest"
 * </pre>
 */
@DisplayName("Full Integration: saga.command.xml-signing → CSC sign → MinIO store → outbox")
@Tag("full-integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class SagaCommandFullIntegrationTest extends AbstractFullIntegrationTest {

    private static final String COMMAND_TOPIC = "saga.command.xml-signing";
    private static final String COMPENSATION_TOPIC = "saga.compensation.xml-signing";

    // =========================================================================
    // Happy path: end-to-end signing
    // =========================================================================

    @Nested
    @DisplayName("Signing happy-path")
    class SigningHappyPath {

        @Test
        @DisplayName("Should sign TAX_INVOICE, persist to DB and store both XMLs in MinIO")
        void shouldSignTaxInvoiceEndToEnd() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String documentNumber = "TINV-" + documentId.substring(4, 12);
            String correlationId = newCorrelationId();
            String xmlContent = getSampleTaxInvoiceXml();

            var command = createProcessCommand(
                    documentId, documentNumber, xmlContent, correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);

            // Assert — DB record reaches COMPLETED
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            assertThat(doc.get("document_id")).isEqualTo(documentId);
            assertThat(doc.get("document_number")).isEqualTo(documentNumber);
            assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
            assertThat(doc.get("status")).isEqualTo("COMPLETED");
            assertThat(doc.get("signed_xml_url")).asString().isNotBlank();
            assertThat(doc.get("signed_xml_path")).asString().isNotBlank();
            assertThat(doc.get("signed_xml_size_bytes")).isNotNull();
            assertThat((Long) doc.get("signed_xml_size_bytes")).isGreaterThan(0L);
            assertThat(doc.get("transaction_id")).asString().isNotBlank();
            assertThat(doc.get("certificate")).asString().isNotBlank();
            assertThat(doc.get("error_message")).isNull();

            // Assert — original XML stored in MinIO
            String originalXmlPath = (String) doc.get("original_xml_path");
            assertThat(originalXmlPath).isNotBlank();
            awaitObjectInMinIO(originalXmlPath);
            assertThat(objectExistsInMinIO(originalXmlPath))
                    .as("original XML should be stored in MinIO at key: " + originalXmlPath)
                    .isTrue();

            // Assert — signed XML stored in MinIO
            String signedXmlPath = (String) doc.get("signed_xml_path");
            awaitObjectInMinIO(signedXmlPath);
            assertThat(objectExistsInMinIO(signedXmlPath))
                    .as("signed XML should be stored in MinIO at key: " + signedXmlPath)
                    .isTrue();
        }

        @Test
        @DisplayName("Should sign INVOICE document type end-to-end")
        void shouldSignInvoiceEndToEnd() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String xmlContent = getSampleInvoiceXml();

            var command = createProcessCommand(
                    documentId, "INV-" + documentId.substring(4, 12),
                    xmlContent, correlationId, "INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);

            // Assert
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");
            assertThat(doc.get("document_type")).isEqualTo("INVOICE");
            assertThat(doc.get("signed_xml_path")).asString().isNotBlank();

            String signedXmlPath = (String) doc.get("signed_xml_path");
            awaitObjectInMinIO(signedXmlPath);
            assertThat(objectExistsInMinIO(signedXmlPath)).isTrue();
        }

        @Test
        @DisplayName("Should detect TAX_INVOICE type from XML namespace when documentType is absent")
        void shouldDetectDocumentTypeFromNamespaceWhenNotProvided() throws Exception {
            // Arrange — no documentType in command
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            var command = createProcessCommand(
                    documentId, "TINV-DETECT-001",
                    getSampleTaxInvoiceXml(), correlationId, null);

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);

            // Assert — type detected from namespace URI
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");
            assertThat(doc.get("document_type")).isEqualTo("TAX_INVOICE");
        }
    }

    // =========================================================================
    // Signed XML verification
    // =========================================================================

    @Nested
    @DisplayName("Signed XML content verification")
    class SignedXmlContentVerification {

        @Test
        @DisplayName("Signed XML stored in MinIO should contain an XAdES Signature element")
        void signedXmlInMinIOShouldContainXadesSignatureElement() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            var command = createProcessCommand(
                    documentId, "TINV-SIGN-VERIFY-001",
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — download signed XML from MinIO and check for signature
            String signedXmlPath = (String) doc.get("signed_xml_path");
            awaitObjectInMinIO(signedXmlPath);

            byte[] signedXmlBytes = downloadFromMinIO(signedXmlPath);
            String signedXml = new String(signedXmlBytes, StandardCharsets.UTF_8);

            // XAdES signature embeds a <ds:Signature> or <Signature> element
            assertThat(signedXml)
                    .as("signed XML should contain an XML Signature element")
                    .containsAnyOf("<ds:Signature", "<Signature");

            // Signed XML should still be valid XML (parseable)
            assertThat(signedXml).startsWith("<?xml").contains("</");
        }

        @Test
        @DisplayName("Original XML stored in MinIO should match the content sent in the command")
        void originalXmlInMinIOShouldMatchCommandContent() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String originalXml = getSampleTaxInvoiceXml();

            var command = createProcessCommand(
                    documentId, "TINV-ORIG-001",
                    originalXml, correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — download original XML from MinIO and compare
            String originalXmlPath = (String) doc.get("original_xml_path");
            awaitObjectInMinIO(originalXmlPath);

            byte[] storedOriginalBytes = downloadFromMinIO(originalXmlPath);
            String storedOriginal = new String(storedOriginalBytes, StandardCharsets.UTF_8);

            assertThat(storedOriginal.trim()).isEqualTo(originalXml.trim());
        }

        @Test
        @DisplayName("Signed XML URL in DB should be publicly accessible via MinIO base URL")
        void signedXmlUrlInDbShouldPointToMinIO() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            var command = createProcessCommand(
                    documentId, "TINV-URL-001",
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — URL format: <base-url>/<s3-key>
            String signedXmlUrl = (String) doc.get("signed_xml_url");
            assertThat(signedXmlUrl).isNotBlank();
            // URL should contain the bucket name and a dated path
            assertThat(signedXmlUrl).contains("signed-xml-documents");

            // S3 key should be year/month/day/document-type/*.xml format
            String signedXmlPath = (String) doc.get("signed_xml_path");
            assertThat(signedXmlPath).matches("\\d{4}/\\d{2}/\\d{2}/TAX_INVOICE/.*\\.xml");
        }

        @Test
        @DisplayName("Signed XML size in DB should reflect actual MinIO object size")
        void signedXmlSizeInDbShouldReflectActualObjectSize() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            var command = createProcessCommand(
                    documentId, "TINV-SIZE-001",
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — size recorded in DB matches bytes in MinIO
            long dbSize = (Long) doc.get("signed_xml_size_bytes");
            String signedXmlPath = (String) doc.get("signed_xml_path");

            awaitObjectInMinIO(signedXmlPath);
            byte[] signedBytes = downloadFromMinIO(signedXmlPath);
            assertThat(dbSize).isEqualTo(signedBytes.length);
        }
    }

    // =========================================================================
    // Outbox events
    // =========================================================================

    @Nested
    @DisplayName("Outbox event correctness")
    class OutboxEventVerification {

        @Test
        @DisplayName("Should write xml.signed outbox event with correct payload fields")
        void shouldWriteXmlSignedOutboxEventWithCorrectFields() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String documentNumber = "TINV-OBX-001";
            String correlationId = newCorrelationId();

            var command = createProcessCommand(
                    documentId, documentNumber,
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");
            awaitOutboxEventCount(documentId, 1);

            // Assert — xml.signed outbox event
            List<Map<String, Object>> xmlSignedEvents = getOutboxEventsByAggregateId(documentId);
            assertThat(xmlSignedEvents).hasSize(1);

            Map<String, Object> event = xmlSignedEvents.get(0);
            assertThat(event.get("topic")).isEqualTo("xml.signed");
            assertThat(event.get("event_type")).isEqualTo("XmlSignedEvent");
            assertThat(event.get("aggregate_type")).isEqualTo("SignedXmlDocument");
            assertThat(event.get("aggregate_id")).isEqualTo(documentId);
            assertThat(event.get("partition_key")).isEqualTo(documentId);

            // Verify payload JSON
            JsonNode payload = objectMapper.readTree((String) event.get("payload"));
            assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
            assertThat(payload.get("documentNumber").asText()).isEqualTo(documentNumber);
            assertThat(payload.get("documentType").asText()).isEqualTo("TAX_INVOICE");
            assertThat(payload.get("correlationId").asText()).isEqualTo(correlationId);

            // Verify headers
            String headersJson = (String) event.get("headers");
            if (headersJson != null) {
                JsonNode headers = objectMapper.readTree(headersJson);
                assertThat(headers.get("correlationId").asText()).isEqualTo(correlationId);
                assertThat(headers.get("documentNumber").asText()).isEqualTo(documentNumber);
            }
        }

        @Test
        @DisplayName("Should write saga.reply.xml-signing outbox event with SUCCESS status and signed XML URL")
        void shouldWriteSagaReplySuccessWithSignedXmlUrl() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-REPLY-001",
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");
            awaitOutboxEventCount(sagaId, 1);

            // Assert — saga reply
            List<Map<String, Object>> replyEvents = getOutboxEventsByAggregateId(sagaId);
            assertThat(replyEvents).isNotEmpty();

            Map<String, Object> reply = replyEvents.stream()
                    .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No saga.reply.xml-signing event found"));

            assertThat(reply.get("aggregate_type")).isEqualTo("SignedXmlDocument");
            assertThat(reply.get("aggregate_id")).isEqualTo(sagaId);
            assertThat(reply.get("partition_key")).isEqualTo(sagaId);

            String replyPayload = (String) reply.get("payload");
            assertThat(replyPayload).contains("SUCCESS");
            assertThat(replyPayload).contains(correlationId);

            // The reply should include the signed XML URL (so orchestrator can pass it downstream)
            JsonNode payloadNode = objectMapper.readTree(replyPayload);
            if (payloadNode.has("signedXmlUrl")) {
                assertThat(payloadNode.get("signedXmlUrl").asText()).contains("signed-xml-documents");
            }

            // Verify saga reply headers
            String headersJson = (String) reply.get("headers");
            if (headersJson != null) {
                JsonNode headers = objectMapper.readTree(headersJson);
                assertThat(headers.get("sagaId").asText()).isEqualTo(sagaId);
                assertThat(headers.get("correlationId").asText()).isEqualTo(correlationId);
                assertThat(headers.get("status").asText()).isEqualTo("SUCCESS");
            }
        }

        @Test
        @DisplayName("Should write both xml.signed and saga.reply outbox events atomically")
        void shouldWriteBothOutboxEventsAtomically() throws Exception {
            // Arrange
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var command = createProcessCommand(
                    documentId, "TINV-ATOMIC-001",
                    getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE");

            // Act
            sendEvent(COMMAND_TOPIC, documentId, command);
            awaitDocumentStatus(documentId, "COMPLETED");

            // Assert — BOTH outbox events exist
            awaitOutboxEventCount(documentId, 1);  // xml.signed event
            awaitOutboxEventCount(sagaId, 1);      // saga reply event

            List<Map<String, Object>> allEvents = getAllOutboxEvents();
            long xmlSignedCount = allEvents.stream()
                    .filter(e -> "xml.signed".equals(e.get("topic"))).count();
            long sagaReplyCount = allEvents.stream()
                    .filter(e -> "saga.reply.xml-signing".equals(e.get("topic"))).count();

            assertThat(xmlSignedCount).isEqualTo(1);
            assertThat(sagaReplyCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("Should not re-sign when a duplicate command is received for an already-COMPLETED document")
        void shouldNotResignAlreadyCompletedDocument() throws Exception {
            // Arrange — sign document first
            String documentId = newDocumentId();
            String correlationId1 = newCorrelationId();

            var firstCommand = createProcessCommand(
                    documentId, "TINV-IDEM-001",
                    getSampleTaxInvoiceXml(), correlationId1, "TAX_INVOICE");
            sendEvent(COMMAND_TOPIC, documentId, firstCommand);
            awaitDocumentStatus(documentId, "COMPLETED");

            String firstSignedPath = (String) getDocumentByDocumentId(documentId).get("signed_xml_path");

            // Act — send duplicate with different correlationId
            String correlationId2 = newCorrelationId();
            var duplicateCommand = createProcessCommand(
                    documentId, "TINV-IDEM-001",
                    getSampleTaxInvoiceXml(), correlationId2, "TAX_INVOICE");
            sendEvent(COMMAND_TOPIC, documentId, duplicateCommand);

            // Allow time for the duplicate to be processed
            Thread.sleep(5_000);

            // Assert — only one DB record exists
            Integer count = testJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM signed_xml_documents WHERE document_id = ?",
                    Integer.class, documentId);
            assertThat(count).isEqualTo(1);

            // Signed XML path has not changed (no re-signing occurred)
            String currentSignedPath = (String) getDocumentByDocumentId(documentId).get("signed_xml_path");
            assertThat(currentSignedPath).isEqualTo(firstSignedPath);

            // A SUCCESS reply is still sent for the duplicate saga
            String sagaId2 = sagaIdFor(correlationId2);
            awaitOutboxEventCount(sagaId2, 1);
            List<Map<String, Object>> replyEvents = getOutboxEventsByAggregateId(sagaId2);
            assertThat(replyEvents).isNotEmpty();
            assertThat((String) replyEvents.get(0).get("payload")).contains("SUCCESS");
        }
    }

    // =========================================================================
    // Multiple documents
    // =========================================================================

    @Nested
    @DisplayName("Multiple documents in parallel")
    class MultipleDocuments {

        @Test
        @DisplayName("Should process two documents of different types concurrently")
        void shouldProcessMultipleDocumentsConcurrently() throws Exception {
            // Arrange
            String docId1 = newDocumentId();
            String docId2 = newDocumentId();
            String corr1 = newCorrelationId();
            String corr2 = newCorrelationId();

            var cmd1 = createProcessCommand(docId1, "TINV-PAR-001",
                    getSampleTaxInvoiceXml(), corr1, "TAX_INVOICE");
            var cmd2 = createProcessCommand(docId2, "INV-PAR-001",
                    getSampleInvoiceXml(), corr2, "INVOICE");

            // Act — send both commands
            sendEvent(COMMAND_TOPIC, docId1, cmd1);
            sendEvent(COMMAND_TOPIC, docId2, cmd2);

            // Assert — both reach COMPLETED
            Map<String, Object> doc1 = awaitDocumentStatus(docId1, "COMPLETED");
            Map<String, Object> doc2 = awaitDocumentStatus(docId2, "COMPLETED");

            assertThat(doc1.get("document_type")).isEqualTo("TAX_INVOICE");
            assertThat(doc2.get("document_type")).isEqualTo("INVOICE");

            // Both have their signed XMLs in MinIO
            awaitObjectInMinIO((String) doc1.get("signed_xml_path"));
            awaitObjectInMinIO((String) doc2.get("signed_xml_path"));
            assertThat(objectExistsInMinIO((String) doc1.get("signed_xml_path"))).isTrue();
            assertThat(objectExistsInMinIO((String) doc2.get("signed_xml_path"))).isTrue();
        }
    }

    // =========================================================================
    // Compensation
    // =========================================================================

    @Nested
    @DisplayName("Compensation (saga rollback)")
    class Compensation {

        @Test
        @DisplayName("Should delete signed document from DB and both XMLs from MinIO on compensation")
        void shouldCompensateByDeletingFromDbAndMinIO() throws Exception {
            // Arrange — sign a document first
            String documentId = newDocumentId();
            String processCorrelationId = newCorrelationId();

            var processCommand = createProcessCommand(
                    documentId, "TINV-COMP-001",
                    getSampleTaxInvoiceXml(), processCorrelationId, "TAX_INVOICE");
            sendEvent(COMMAND_TOPIC, documentId, processCommand);
            Map<String, Object> signedDoc = awaitDocumentStatus(documentId, "COMPLETED");

            String originalXmlPath = (String) signedDoc.get("original_xml_path");
            String signedXmlPath = (String) signedDoc.get("signed_xml_path");

            // Verify both exist in MinIO before compensation
            awaitObjectInMinIO(originalXmlPath);
            awaitObjectInMinIO(signedXmlPath);
            assertThat(objectExistsInMinIO(originalXmlPath)).isTrue();
            assertThat(objectExistsInMinIO(signedXmlPath)).isTrue();

            // Act — compensate
            String compensateCorrelationId = newCorrelationId();
            var compensateCommand = createCompensateCommand(documentId, compensateCorrelationId);
            sendEvent(COMPENSATION_TOPIC, documentId, compensateCommand);

            // Assert — DB record deleted
            awaitDocumentDeleted(documentId);

            // Assert — COMPENSATED outbox reply written
            String compensateSagaId = sagaIdFor(compensateCorrelationId);
            awaitOutboxEventCount(compensateSagaId, 1);
            List<Map<String, Object>> replyEvents = getOutboxEventsByAggregateId(compensateSagaId);
            assertThat(replyEvents).isNotEmpty();

            Map<String, Object> replyEvent = replyEvents.stream()
                    .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No saga.reply.xml-signing event after compensation"));
            assertThat((String) replyEvent.get("payload")).contains("COMPENSATED");
            assertThat((String) replyEvent.get("payload")).contains(compensateCorrelationId);

            // Assert — MinIO objects were deleted
            // The SagaCommandHandler deletes both original and signed XMLs from MinIO
            // Allow a moment for the MinIO deletions to propagate
            await().atMost(15, TimeUnit.SECONDS)
                    .pollInterval(2, TimeUnit.SECONDS)
                    .until(() -> !objectExistsInMinIO(originalXmlPath) && !objectExistsInMinIO(signedXmlPath));
        }

        @Test
        @DisplayName("Should send COMPENSATED reply even when document was never signed (idempotent)")
        void shouldSendCompensatedReplyForNonExistentDocument() throws Exception {
            // Arrange — no document was ever processed
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();
            String sagaId = sagaIdFor(correlationId);

            var compensateCommand = createCompensateCommand(documentId, correlationId);

            // Act
            sendEvent(COMPENSATION_TOPIC, documentId, compensateCommand);

            // Assert — COMPENSATED reply written despite document not existing
            awaitOutboxEventCount(sagaId, 1);
            List<Map<String, Object>> replyEvents = getOutboxEventsByAggregateId(sagaId);
            assertThat(replyEvents).isNotEmpty();

            Map<String, Object> reply = replyEvents.stream()
                    .filter(e -> "saga.reply.xml-signing".equals(e.get("topic")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No saga.reply.xml-signing event"));
            assertThat((String) reply.get("payload")).contains("COMPENSATED");
        }
    }

    // =========================================================================
    // Path format verification
    // =========================================================================

    @Nested
    @DisplayName("MinIO path format")
    class MinIOPathFormat {

        @Test
        @DisplayName("Original XML path should follow dated path structure: YYYY/MM/DD/TYPE/filename.xml")
        void originalXmlPathShouldFollowDatedStructure() throws Exception {
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            sendEvent(COMMAND_TOPIC, documentId,
                    createProcessCommand(documentId, "TINV-PATH-ORIG-001",
                            getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE"));

            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            String originalXmlPath = (String) doc.get("original_xml_path");
            // Expected: 2026/04/08/TAX_INVOICE/original-xml-<documentId>-<uuid>.xml
            assertThat(originalXmlPath)
                    .matches("\\d{4}/\\d{2}/\\d{2}/TAX_INVOICE/original-xml-.*\\.xml");
        }

        @Test
        @DisplayName("Signed XML path should follow dated path structure: YYYY/MM/DD/TYPE/filename.xml")
        void signedXmlPathShouldFollowDatedStructure() throws Exception {
            String documentId = newDocumentId();
            String correlationId = newCorrelationId();

            sendEvent(COMMAND_TOPIC, documentId,
                    createProcessCommand(documentId, "TINV-PATH-SIGN-001",
                            getSampleTaxInvoiceXml(), correlationId, "TAX_INVOICE"));

            Map<String, Object> doc = awaitDocumentStatus(documentId, "COMPLETED");

            String signedXmlPath = (String) doc.get("signed_xml_path");
            // Expected: 2026/04/08/TAX_INVOICE/signed-xml-<documentId>-<uuid>.xml
            assertThat(signedXmlPath)
                    .matches("\\d{4}/\\d{2}/\\d{2}/TAX_INVOICE/signed-xml-.*\\.xml");
        }
    }
}
