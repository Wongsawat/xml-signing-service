package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.integration.config.FullIntegrationTestConfiguration;
import com.wpanther.xmlsigning.integration.config.TestKafkaProducerConfig;
import com.wpanther.xmlsigning.integration.support.EidasRemoteSigningTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.wpanther.xmlsigning.integration.config.FullIntegrationTestConfiguration.objectExistsInBucket;
import static org.awaitility.Awaitility.await;

/**
 * Base class for full end-to-end integration tests.
 *
 * <p>Unlike {@link AbstractKafkaConsumerTest}, this class does <strong>not</strong> mock:
 * <ul>
 *   <li>{@code CSCAuthClient} — calls the real eidasremotesigning service on {@code localhost:9000}</li>
 *   <li>{@code CSCSignatureClient} — calls the real eidasremotesigning service</li>
 *   <li>{@code XmlStoragePort} — uses the real MinIO adapter to store XML in {@code localhost:9100}</li>
 * </ul>
 *
 * <p><strong>Required containers</strong> (start with the {@code --full-integration} flag):
 * <pre>
 *   cd invoice-microservices
 *   ./scripts/test-containers-start.sh --with-eidas --with-debezium --auto-deploy-connectors
 * </pre>
 *
 * <p><strong>Run tests</strong>:
 * <pre>
 *   mvn test -Pintegration -Dtest="SagaCommandFullIntegrationTest"
 * </pre>
 *
 * <p><strong>How eidasremotesigning is bootstrapped</strong>:
 * {@link EidasRemoteSigningTestHelper#setupOnce} runs inside {@link #configureEidasProperties}
 * (a static {@link DynamicPropertySource} method that executes before the Spring context is created).
 * It registers an OAuth2 client, fetches a Bearer token, and inserts a BCFKS signing credential
 * into the eidasremotesigning database. The credential uses the pre-mounted keystore at
 * {@code /app/keystores/eidas-signing.bfks} inside the container.
 *
 * <p>The {@link FullIntegrationTestConfiguration} registers a Feign {@code RequestInterceptor}
 * that attaches {@code Authorization: Bearer <token>} to every CSC API call, satisfying the
 * JWT resource server security on the eidasremotesigning service.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.bootstrap-servers=${KAFKA_BROKERS:localhost:9093}",
                "KAFKA_BROKERS=localhost:9093"
        }
)
@ActiveProfiles("full-integration-test")
@Import({TestKafkaProducerConfig.class, FullIntegrationTestConfiguration.class})
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFullIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractFullIntegrationTest.class);

    private static final String EIDAS_BASE_URL = "http://localhost:9000";
    private static final String EIDAS_PG_JDBC_URL =
            "jdbc:postgresql://localhost:5433/eidasremotesigning";
    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "postgres";

    /** Set once in {@link #configureEidasProperties} before the Spring context is created. */
    protected static volatile EidasRemoteSigningTestHelper.SetupResult eidasSetup;

    // ----- Spring-injected beans -----

    @Autowired
    protected KafkaTemplate<String, String> testKafkaTemplate;

    @Autowired
    @Qualifier("fullIntegrationJdbcTemplate")
    protected JdbcTemplate testJdbcTemplate;

    @Autowired
    @Qualifier("minioVerificationS3Client")
    protected S3Client minioVerificationS3Client;

    @Value("${app.minio.bucket-name}")
    protected String minioBucketName;

    protected ObjectMapper objectMapper;

    // ----- Lifecycle -----

    /**
     * Runs before the Spring context is created.
     * Sets {@code app.csc.client-id} and {@code app.csc.credential-id} dynamically so
     * {@link com.wpanther.xmlsigning.application.usecase.XmlSigningServiceImpl} picks up
     * the credential that was created in eidasremotesigning.
     */
    @DynamicPropertySource
    static void configureEidasProperties(DynamicPropertyRegistry registry) {
        if (!"true".equals(System.getProperty("integration.tests.enabled"))) {
            // Test is disabled — set placeholder values to satisfy @Value injection
            registry.add("app.csc.client-id", () -> "integration-not-enabled");
            registry.add("app.csc.credential-id", () -> "integration-not-enabled");
            return;
        }
        try {
            eidasSetup = EidasRemoteSigningTestHelper.setupOnce(
                    EIDAS_BASE_URL, EIDAS_PG_JDBC_URL, PG_USER, PG_PASSWORD);
            registry.add("app.csc.client-id", () -> eidasSetup.clientId());
            registry.add("app.csc.credential-id", () -> eidasSetup.credentialId());
            log.info("[AbstractFullIntegrationTest] eidasremotesigning setup complete: " +
                    "clientId={}, credentialId={}", eidasSetup.clientId(), eidasSetup.credentialId());
        } catch (Exception e) {
            log.error("[AbstractFullIntegrationTest] eidasremotesigning setup failed: {}", e.getMessage());
            // Fallback — test will fail at runtime with a clearer error
            registry.add("app.csc.client-id", () -> "eidas-setup-failed");
            registry.add("app.csc.credential-id", () -> "eidas-setup-failed");
        }
    }

    @BeforeAll
    void setUpObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach
    void cleanDatabase() {
        testJdbcTemplate.execute("DELETE FROM outbox_events");
        testJdbcTemplate.execute("DELETE FROM signed_xml_documents");
    }

    // ----- Command factory helpers -----

    protected ProcessXmlSigningCommand createProcessCommand(
            String documentId,
            String documentNumber,
            String xmlContent,
            String correlationId,
            String documentType) {

        String sagaId = "saga-" + correlationId;
        return new ProcessXmlSigningCommand(
                sagaId, SagaStep.SIGN_XML, correlationId,
                documentId, xmlContent, documentNumber, documentType);
    }

    protected CompensateXmlSigningCommand createCompensateCommand(
            String documentId,
            String correlationId) {

        String sagaId = "saga-" + correlationId;
        return new CompensateXmlSigningCommand(
                sagaId, SagaStep.SIGN_XML, correlationId,
                SagaStep.SIGN_XML.name(), documentId, "TAX_INVOICE");
    }

    // ----- Kafka helpers -----

    protected void sendEvent(String topic, String key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            testKafkaTemplate.send(topic, key, json).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send event to topic: " + topic, e);
        }
    }

    // ----- DB query helpers -----

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

    // ----- Await helpers -----

    protected Map<String, Object> awaitDocumentStatus(String documentId, String expectedStatus) {
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    Map<String, Object> doc = getDocumentByDocumentId(documentId);
                    return doc != null && expectedStatus.equals(doc.get("status"));
                });
        return getDocumentByDocumentId(documentId);
    }

    protected void awaitOutboxEventCount(String aggregateId, int expectedCount) {
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> getOutboxEventsByAggregateId(aggregateId).size() >= expectedCount);
    }

    protected void awaitDocumentDeleted(String documentId) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> getDocumentByDocumentId(documentId) == null);
    }

    // ----- MinIO helpers -----

    /**
     * Returns {@code true} if the given S3 key exists in {@link #minioBucketName}.
     */
    protected boolean objectExistsInMinIO(String s3Key) {
        return objectExistsInBucket(minioVerificationS3Client, minioBucketName, s3Key);
    }

    /**
     * Downloads the bytes of the object at {@code s3Key} from MinIO.
     *
     * @throws NoSuchKeyException if the object does not exist
     */
    protected byte[] downloadFromMinIO(String s3Key) {
        return minioVerificationS3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(minioBucketName)
                        .key(s3Key)
                        .build()
        ).asByteArray();
    }

    /**
     * Waits until the given S3 key appears in MinIO (up to 2 minutes).
     */
    protected void awaitObjectInMinIO(String s3Key) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> objectExistsInMinIO(s3Key));
    }

    // ----- XML fixture helpers -----

    protected String loadTestXml(String filename) throws IOException {
        Path path = Path.of("src/test/resources/samples", filename);
        return Files.readString(path);
    }

    protected String getSampleTaxInvoiceXml() throws IOException {
        return loadTestXml("tax-invoice-sample.xml");
    }

    protected String getSampleInvoiceXml() throws IOException {
        return loadTestXml("invoice-sample.xml");
    }

    // ----- ID generators -----

    protected String newDocumentId() {
        return "DOC-" + UUID.randomUUID();
    }

    protected String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    protected String sagaIdFor(String correlationId) {
        return "saga-" + correlationId;
    }
}
