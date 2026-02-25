package com.wpanther.xmlsigning.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.integration.config.CdcTestConfiguration;
import com.wpanther.xmlsigning.integration.config.TestKafkaConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for CDC integration tests.
 * <p>
 * Requires external containers:
 * - PostgreSQL on port 5433
 * - Kafka on port 9093
 * - Debezium Connect on port 8083
 * <p>
 * Start with: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
 */
@SpringBootTest(
    classes = CdcTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("cdc-test")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCdcIntegrationTest {

    protected static final String DEBEZIUM_URL = "http://localhost:8083";
    protected static final String DEBEZIUM_CONNECTOR_NAME = "outbox-connector-xmlsigning";

    protected static final String OUTPUT_TOPIC = "xml.signed";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected KafkaConsumer<String, String> testKafkaConsumer;

    @Autowired
    protected TestKafkaConsumerConfig kafkaConsumerConfig;

    @MockBean
    protected XmlSigningService signingService;

    @MockBean
    protected CSCAuthClient authClient;

    @MockBean
    protected CSCSignatureClient signatureClient;

    protected ObjectMapper objectMapper;

    @BeforeAll
    void setupInfrastructure() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Verify PostgreSQL is accessible
        Integer pgResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(pgResult).isEqualTo(1);

        // Verify Kafka is accessible by creating output topics
        kafkaConsumerConfig.createTopics();

        // Verify Debezium Connect is running
        verifyDebeziumConnectRunning();

        // Subscribe consumer to xml.signed topic
        testKafkaConsumer.subscribe(List.of(OUTPUT_TOPIC));
    }

    @BeforeEach
    void cleanupDatabase() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM signed_xml_documents");

        // Drain any stale messages from previous tests
        drainConsumer();
    }

    // --- Helper Methods ---

    /**
     * Insert an outbox event directly into the database.
     * outbox_events.payload is TEXT, NOT JSONB.
     */
    protected void insertOutboxEvent(String aggregateType, String aggregateId,
                                      String eventType, String payload,
                                      String topic, String partitionKey, String headers) {
        jdbcTemplate.update(
            "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, " +
            "topic, partition_key, headers, status, retry_count, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)",
            UUID.randomUUID(), aggregateType, aggregateId, eventType, payload,
            topic, partitionKey, headers
        );
    }

    /**
     * Parse a Debezium CDC message value, handling double-encoded payloads.
     * <p>
     * Debezium delivers TEXT column values as JSON-quoted strings. When the outbox
     * payload column is TEXT (not JSONB), the value arrives double-encoded:
     * e.g. {@code "{\"invoiceId\":\"...\"}"} instead of {@code {"invoiceId":"..."}}.
     * This method detects and unwraps that encoding.
     */
    protected JsonNode parseDebeziumPayload(String rawValue) throws Exception {
        JsonNode node = objectMapper.readTree(rawValue);
        // If Debezium wrapped the TEXT payload as a JSON string, unwrap it
        if (node.isTextual()) {
            return objectMapper.readTree(node.asText());
        }
        return node;
    }

    /**
     * Poll Kafka for messages on subscribed topics, waiting up to the given timeout.
     */
    protected List<ConsumerRecord<String, String>> pollForMessages(Duration timeout) {
        List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = testKafkaConsumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                allRecords.add(record);
            }
            if (!allRecords.isEmpty()) {
                break;
            }
        }

        return allRecords;
    }

    /**
     * Poll Kafka for messages on a specific topic that contain the expected content.
     * Filters out stale messages from previous test runs.
     */
    protected List<ConsumerRecord<String, String>> pollForMessagesOnTopic(
            String targetTopic, String expectedContent, Duration timeout) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = testKafkaConsumer.poll(Duration.ofSeconds(2));
            for (ConsumerRecord<String, String> record : records) {
                if (record.topic().equals(targetTopic) && record.value().contains(expectedContent)) {
                    matching.add(record);
                }
            }
            if (!matching.isEmpty()) {
                break;
            }
        }

        return matching;
    }

    /**
     * Poll Kafka for messages on a specific topic (without content filtering).
     */
    protected List<ConsumerRecord<String, String>> pollForMessagesOnTopic(
            String targetTopic, Duration timeout) {
        return pollForMessagesOnTopic(targetTopic, "", timeout);
    }

    protected List<Map<String, Object>> getOutboxEvents() {
        return jdbcTemplate.queryForList("SELECT * FROM outbox_events ORDER BY created_at");
    }

    protected List<Map<String, Object>> getOutboxEventsByTopic(String topic) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM outbox_events WHERE topic = ? ORDER BY created_at", topic);
    }

    /**
     * Drain any pending messages from the consumer to avoid interference between tests.
     */
    private void drainConsumer() {
        testKafkaConsumer.poll(Duration.ofSeconds(1));
    }

    /**
     * Verify that Debezium Connect REST API is reachable.
     */
    private void verifyDebeziumConnectRunning() throws Exception {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                URI.create(DEBEZIUM_URL + "/connectors").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            assertThat(status).as("Debezium Connect should be reachable").isEqualTo(200);
            conn.disconnect();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Debezium Connect not accessible at " + DEBEZIUM_URL +
                ". Start containers with: ./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors", e);
        }
    }
}
