package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.application.port.out.XmlStoragePort;
import com.wpanther.xmlsigning.domain.model.StorageResult;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.integration.config.ConsumerTestConfiguration;
import com.wpanther.xmlsigning.integration.config.TestKafkaProducerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9093",
        "KAFKA_BROKERS=localhost:9093"
    }
)
@ActiveProfiles("consumer-test")
@Import({TestKafkaProducerConfig.class, ConsumerTestConfiguration.class})
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractKafkaConsumerTest {

    @Autowired
    protected KafkaTemplate<String, String> testKafkaTemplate;

    @Autowired
    protected JdbcTemplate testJdbcTemplate;

    @MockBean
    protected CSCAuthClient authClient;

    @MockBean
    protected CSCSignatureClient signatureClient;

    @MockBean
    protected XmlStoragePort xmlStoragePort;

    protected ObjectMapper objectMapper;

    @BeforeAll
    void setupObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setupMockResponses() throws Exception {
        // 1. Mock CSC authorization endpoint - returns SAD token
        CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
            .SAD("test-sad-token-" + UUID.randomUUID())
            .transactionID("test-txn-" + UUID.randomUUID())
            .expiresIn(300L)
            .build();
        when(authClient.authorize(any())).thenReturn(authResponse);

        // 2. Mock CSC signHash endpoint - returns raw signature and certificate
        String rawSignature = Base64.getEncoder().encodeToString("test-raw-signature-bytes".getBytes());
        String certificate = Base64.getEncoder().encodeToString("test-certificate".getBytes());

        CSCSignatureResponse signResponse = CSCSignatureResponse.builder()
            .signatureAlgorithm("SHA256withRSA")
            .signatures(new String[]{rawSignature})
            .certificate(certificate)
            .build();
        when(signatureClient.signHash(any())).thenReturn(signResponse);

        // 3. Mock MinIO storage operations
        when(xmlStoragePort.storeOriginalXml(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                String documentId = invocation.getArgument(0);
                String docType = invocation.getArgument(1);
                return new XmlStorageKey("original/" + docType + "/" + documentId + ".xml");
            });

        when(xmlStoragePort.storeSignedXml(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                String documentId = invocation.getArgument(0);
                String docType = invocation.getArgument(1);
                return new StorageResult(
                    new XmlStorageKey("signed/" + docType + "/" + documentId + ".xml"),
                    2048L
                );
            });

        when(xmlStoragePort.buildUrl(any()))
            .thenAnswer(invocation -> {
                XmlStorageKey key = invocation.getArgument(0);
                return "http://localhost:9001/xmlsigning/" + key.value();
            });

        // 4. Clean database (order matters for FK constraints)
        testJdbcTemplate.execute("DELETE FROM outbox_events");
        testJdbcTemplate.execute("DELETE FROM signed_xml_documents");
    }

    // --- Event Sending ---

    protected void sendEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            testKafkaTemplate.send(topic, key, json).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to topic: " + topic, e);
        }
    }

    protected ProcessXmlSigningCommand createProcessCommand(
            String documentId, String documentNumber, String xmlContent,
            String correlationId, String documentType) {
        String sagaId = "saga-" + correlationId;
        return new ProcessXmlSigningCommand(
            sagaId, SagaStep.SIGN_XML, correlationId,
            documentId, xmlContent, documentNumber, documentType
        );
    }

    protected CompensateXmlSigningCommand createCompensateCommand(
            String documentId, String correlationId) {
        String sagaId = "saga-" + correlationId;
        return new CompensateXmlSigningCommand(
            sagaId, SagaStep.SIGN_XML, correlationId,
            SagaStep.SIGN_XML.name(), documentId, "TAX_INVOICE"
        );
    }

    // --- Await Helpers ---

    protected Map<String, Object> awaitDocumentStatus(String documentId, String expectedStatus) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(2, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> doc = getDocumentByDocumentId(documentId);
                   return doc != null && expectedStatus.equals(doc.get("status"));
               });
        return getDocumentByDocumentId(documentId);
    }

    protected void awaitOutboxEventCount(String aggregateId, int expectedCount) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getOutboxEventsByAggregateId(aggregateId).size() >= expectedCount);
    }

    protected void assertNoDocumentCreatedAfterWait(String documentId) {
        await().during(15, TimeUnit.SECONDS)
               .atMost(20, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> getDocumentByDocumentId(documentId) == null);
    }

    // --- Database Query Helpers ---

    protected Map<String, Object> getDocumentByDocumentId(String documentId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
                "SELECT * FROM signed_xml_documents WHERE document_id = ?", documentId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected List<Map<String, Object>> getOutboxEventsByAggregateId(String aggregateId) {
        return testJdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
                aggregateId);
    }

    protected List<Map<String, Object>> getAllOutboxEvents() {
        return testJdbcTemplate.queryForList(
                "SELECT * FROM outbox_events ORDER BY created_at");
    }

    protected int getDocumentCount() {
        Integer count = testJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM signed_xml_documents", Integer.class);
        return count != null ? count : 0;
    }

    // --- Test XML Fixtures ---

    protected String loadTestXml(String filename) throws IOException {
        Path path = Path.of("src/test/resources/samples", filename);
        return Files.readString(path);
    }

    /**
     * Returns a minimal valid tax invoice XML suitable for signing tests.
     */
    protected String getSampleTaxInvoiceXml() throws IOException {
        return loadTestXml("tax-invoice-sample.xml");
    }

    /**
     * Returns a minimal valid invoice XML suitable for signing tests.
     */
    protected String getSampleInvoiceXml() throws IOException {
        return loadTestXml("invoice-sample.xml");
    }

    /**
     * Configure mock CSC signature client to fail signing.
     * Call this in a test method BEFORE sending the event to override the default mock.
     */
    protected void setupSigningFailure(String errorMessage) {
        when(signatureClient.signHash(any()))
                .thenThrow(new RuntimeException(errorMessage));
    }
}
