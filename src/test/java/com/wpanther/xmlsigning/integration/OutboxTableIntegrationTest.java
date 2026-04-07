package com.wpanther.xmlsigning.integration;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the outbox_events table schema and operations.
 * <p>
 * Requires: PostgreSQL:5433
 * Start with: ./scripts/test-containers-start.sh
 */
@DisplayName("Outbox Table Integration Tests")
class OutboxTableIntegrationTest extends AbstractCdcIntegrationTest {

    @Nested
    @DisplayName("Table Schema")
    class TableSchema {

        @Test
        @DisplayName("Should have outbox_events table with all required columns")
        void shouldHaveAllRequiredColumns() {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'outbox_events' " +
                "ORDER BY ordinal_position"
            );

            assertThat(columns).isNotEmpty();

            // Extract column names
            List<String> columnNames = columns.stream()
                .map(col -> col.get("column_name").toString())
                .toList();

            assertThat(columnNames).contains(
                "id", "aggregate_type", "aggregate_id", "event_type",
                "payload", "topic", "partition_key", "headers",
                "status", "retry_count", "error_message",
                "created_at", "published_at"
            );
        }

        @Test
        @DisplayName("Should have expected indexes on outbox_events")
        void shouldHaveExpectedIndexes() {
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'outbox_events'"
            );

            List<String> indexNames = indexes.stream()
                .map(idx -> idx.get("indexname").toString())
                .toList();

            assertThat(indexNames).contains(
                "idx_outbox_status",
                "idx_outbox_created",
                "idx_outbox_aggregate"
            );
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("Should write and read outbox event with all fields")
        void shouldWriteAndReadOutboxEvent() {
            // Given
            UUID eventId = UUID.randomUUID();
            String aggregateType = "SignedXmlDocument";
            String aggregateId = UUID.randomUUID().toString();
            String eventType = "XmlSignedEvent";
            String payload = "{\"documentId\":\"" + aggregateId + "\",\"documentType\":\"TAX_INVOICE\"}";
            String topic = "xml.signed";
            String partitionKey = UUID.randomUUID().toString();
            String headers = "{\"correlationId\":\"" + partitionKey + "\",\"documentType\":\"TAX_INVOICE\"}";

            // When - payload is TEXT, not JSONB
            jdbcTemplate.update(
                "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, " +
                "payload, topic, partition_key, headers, status, retry_count, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)",
                eventId, aggregateType, aggregateId, eventType,
                payload, topic, partitionKey, headers
            );

            // Then
            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT * FROM outbox_events WHERE id = ?", eventId);

            assertThat(events).hasSize(1);
            Map<String, Object> event = events.get(0);

            assertThat(event.get("aggregate_type")).isEqualTo(aggregateType);
            assertThat(event.get("aggregate_id")).isEqualTo(aggregateId);
            assertThat(event.get("event_type")).isEqualTo(eventType);
            assertThat(event.get("payload").toString()).isEqualTo(payload);
            assertThat(event.get("topic")).isEqualTo(topic);
            assertThat(event.get("partition_key")).isEqualTo(partitionKey);
            assertThat(event.get("headers").toString()).isEqualTo(headers);
            assertThat(event.get("status")).isEqualTo("PENDING");
            assertThat(event.get("retry_count")).isEqualTo(0);
            assertThat(event.get("created_at")).isNotNull();
        }

        @Test
        @DisplayName("Should query outbox events by aggregate ID")
        void shouldQueryByAggregateId() {
            // Given
            String aggregateId = UUID.randomUUID().toString();

            // Insert two events for same aggregate
            insertOutboxEvent("SignedXmlDocument", aggregateId, "XmlSignedEvent",
                "{\"test\":\"event1\"}", "xml.signed", aggregateId, null);
            insertOutboxEvent("SignedXmlDocument", aggregateId, "XmlSignedEvent",
                "{\"test\":\"event2\"}", "xml.signed", aggregateId, null);

            // Insert one event for different aggregate
            insertOutboxEvent("SignedXmlDocument", UUID.randomUUID().toString(), "XmlSignedEvent",
                "{\"test\":\"other\"}", "xml.signed", "other-key", null);

            // When
            List<Map<String, Object>> events = getOutboxEventsByTopic("xml.signed");

            // Then - should find at least the 2 events for our aggregate
            List<Map<String, Object>> matching = events.stream()
                .filter(e -> aggregateId.equals(e.get("aggregate_id")))
                .toList();
            assertThat(matching).hasSize(2);
        }
    }
}
