package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.xmlsigning.domain.event.XmlSigningRequestedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
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
    protected CSCApiClient apiClient;

    protected ObjectMapper objectMapper;

    @BeforeAll
    void setupObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void setupMocksAndCleanup() {
        // Configure mock CSC API responses
        CSCAuthorizeResponse authResponse = CSCAuthorizeResponse.builder()
            .SAD("test-sad-token-" + UUID.randomUUID())
            .transactionID("test-txn-" + UUID.randomUUID())
            .expiresIn(300L)
            .build();
        when(authClient.authorize(any())).thenReturn(authResponse);

        // Base64-encode a "signed" XML for the mock response
        String signedXml = "<signed>test-signed-xml</signed>";
        String signedXmlBase64 = Base64.getEncoder().encodeToString(signedXml.getBytes());
        CSCSignDocumentResponse signResponse = CSCSignDocumentResponse.builder()
            .signedDocument(signedXmlBase64)
            .signatureAlgorithm("SHA256withRSA")
            .certificate("test-certificate-base64")
            .build();
        when(apiClient.signDocument(any())).thenReturn(signResponse);

        // Clean database (order matters for FK constraints)
        testJdbcTemplate.execute("DELETE FROM outbox_events");
        testJdbcTemplate.execute("DELETE FROM signed_xml_documents");
    }

    // --- Helper Methods ---

    protected void sendEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            testKafkaTemplate.send(topic, key, json).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to topic: " + topic, e);
        }
    }

    protected XmlSigningRequestedEvent createSigningRequestEvent(
            String invoiceId, String invoiceNumber, String xmlContent,
            String correlationId, DocumentType documentType) {
        return new XmlSigningRequestedEvent(
            invoiceId, invoiceNumber, xmlContent, "{}", correlationId, documentType
        );
    }

    protected Map<String, Object> awaitDocumentStatus(String invoiceId, String expectedStatus) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(2, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> doc = getDocumentByInvoiceId(invoiceId);
                   return doc != null && expectedStatus.equals(doc.get("status"));
               });
        return getDocumentByInvoiceId(invoiceId);
    }

    protected Map<String, Object> getDocumentByInvoiceId(String invoiceId) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM signed_xml_documents WHERE invoice_id = ?", invoiceId);
        return results.isEmpty() ? null : results.get(0);
    }

    protected List<Map<String, Object>> getOutboxEventsByAggregateId(String aggregateId) {
        return testJdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
            aggregateId);
    }

    protected String loadTestXml(String filename) throws IOException {
        Path path = Path.of(getClass().getClassLoader()
            .getResource("samples/" + filename).getPath());
        return Files.readString(path);
    }
}
