package com.wpanther.xmlsigning.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutboxEventEntity}.
 */
@DisplayName("OutboxEventEntity")
class OutboxEventEntityTest {

    private OutboxEvent createDomainEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("SignedXmlDocument")
                .aggregateId("inv-001")
                .eventType("XmlSignedEvent")
                .payload("{\"test\":\"data\"}")
                .createdAt(Instant.now())
                .publishedAt(null)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .topic("xml.signed")
                .partitionKey("corr-1")
                .headers("{\"k\":\"v\"}")
                .build();
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder with all fields")
        void testBuilderWithAllFields() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(id)
                    .aggregateType("SignedXmlDocument")
                    .aggregateId("inv-001")
                    .eventType("XmlSignedEvent")
                    .payload("{}")
                    .createdAt(now)
                    .publishedAt(now.plusSeconds(10))
                    .status(OutboxStatus.PUBLISHED)
                    .retryCount(1)
                    .errorMessage(null)
                    .topic("xml.signed")
                    .partitionKey("key-1")
                    .headers("{}")
                    .build();

            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getAggregateType()).isEqualTo("SignedXmlDocument");
            assertThat(entity.getAggregateId()).isEqualTo("inv-001");
            assertThat(entity.getEventType()).isEqualTo("XmlSignedEvent");
            assertThat(entity.getPayload()).isEqualTo("{}");
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getPublishedAt()).isEqualTo(now.plusSeconds(10));
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(entity.getRetryCount()).isEqualTo(1);
            assertThat(entity.getErrorMessage()).isNull();
            assertThat(entity.getTopic()).isEqualTo("xml.signed");
            assertThat(entity.getPartitionKey()).isEqualTo("key-1");
            assertThat(entity.getHeaders()).isEqualTo("{}");
        }
    }

    @Nested
    @DisplayName("fromDomain() Method")
    class FromDomainMethod {

        @Test
        @DisplayName("Converts domain event to entity")
        void testFromDomain() {
            OutboxEvent domainEvent = createDomainEvent();

            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            assertThat(entity.getId()).isEqualTo(domainEvent.getId());
            assertThat(entity.getAggregateType()).isEqualTo(domainEvent.getAggregateType());
            assertThat(entity.getAggregateId()).isEqualTo(domainEvent.getAggregateId());
            assertThat(entity.getEventType()).isEqualTo(domainEvent.getEventType());
            assertThat(entity.getPayload()).isEqualTo(domainEvent.getPayload());
            assertThat(entity.getCreatedAt()).isEqualTo(domainEvent.getCreatedAt());
            assertThat(entity.getPublishedAt()).isEqualTo(domainEvent.getPublishedAt());
            assertThat(entity.getStatus()).isEqualTo(domainEvent.getStatus());
            assertThat(entity.getRetryCount()).isEqualTo(domainEvent.getRetryCount());
            assertThat(entity.getTopic()).isEqualTo(domainEvent.getTopic());
            assertThat(entity.getPartitionKey()).isEqualTo(domainEvent.getPartitionKey());
            assertThat(entity.getHeaders()).isEqualTo(domainEvent.getHeaders());
        }
    }

    @Nested
    @DisplayName("toDomain() Method")
    class ToDomainMethod {

        @Test
        @DisplayName("Converts entity to domain event")
        void testToDomain() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(id)
                    .aggregateType("SignedXmlDocument")
                    .aggregateId("inv-001")
                    .eventType("XmlSignedEvent")
                    .payload("{\"test\":1}")
                    .createdAt(now)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .topic("xml.signed")
                    .partitionKey("pk-1")
                    .headers("{\"h\":\"v\"}")
                    .build();

            OutboxEvent domain = entity.toDomain();

            assertThat(domain.getId()).isEqualTo(id);
            assertThat(domain.getAggregateType()).isEqualTo("SignedXmlDocument");
            assertThat(domain.getAggregateId()).isEqualTo("inv-001");
            assertThat(domain.getEventType()).isEqualTo("XmlSignedEvent");
            assertThat(domain.getPayload()).isEqualTo("{\"test\":1}");
            assertThat(domain.getCreatedAt()).isEqualTo(now);
            assertThat(domain.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(domain.getRetryCount()).isEqualTo(0);
            assertThat(domain.getTopic()).isEqualTo("xml.signed");
            assertThat(domain.getPartitionKey()).isEqualTo("pk-1");
            assertThat(domain.getHeaders()).isEqualTo("{\"h\":\"v\"}");
        }
    }

    @Nested
    @DisplayName("Round Trip Conversion")
    class RoundTripTests {

        @Test
        @DisplayName("Domain to entity and back preserves all fields")
        void testRoundTrip() {
            OutboxEvent original = createDomainEvent();

            OutboxEventEntity entity = OutboxEventEntity.fromDomain(original);
            OutboxEvent result = entity.toDomain();

            assertThat(result.getId()).isEqualTo(original.getId());
            assertThat(result.getAggregateType()).isEqualTo(original.getAggregateType());
            assertThat(result.getAggregateId()).isEqualTo(original.getAggregateId());
            assertThat(result.getEventType()).isEqualTo(original.getEventType());
            assertThat(result.getPayload()).isEqualTo(original.getPayload());
            assertThat(result.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(result.getPublishedAt()).isEqualTo(original.getPublishedAt());
            assertThat(result.getStatus()).isEqualTo(original.getStatus());
            assertThat(result.getRetryCount()).isEqualTo(original.getRetryCount());
            assertThat(result.getTopic()).isEqualTo(original.getTopic());
            assertThat(result.getPartitionKey()).isEqualTo(original.getPartitionKey());
            assertThat(result.getHeaders()).isEqualTo(original.getHeaders());
        }
    }

    @Nested
    @DisplayName("Lifecycle Callbacks")
    class LifecycleCallbacks {

        @Test
        @DisplayName("onCreate sets defaults for null fields")
        void testOnCreateSetsDefaults() {
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.onCreate();

            assertThat(entity.getId()).isNotNull();
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getRetryCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("onCreate does not override existing values")
        void testOnCreateDoesNotOverride() {
            UUID existingId = UUID.randomUUID();
            Instant existingTime = Instant.now().minusSeconds(100);

            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(existingId)
                    .status(OutboxStatus.FAILED)
                    .createdAt(existingTime)
                    .retryCount(5)
                    .build();

            entity.onCreate();

            assertThat(entity.getId()).isEqualTo(existingId);
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(entity.getCreatedAt()).isEqualTo(existingTime);
            assertThat(entity.getRetryCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("No-Args Constructor")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("No-args constructor creates entity with null fields")
        void testNoArgsConstructor() {
            OutboxEventEntity entity = new OutboxEventEntity();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getAggregateType()).isNull();
            assertThat(entity.getAggregateId()).isNull();
            assertThat(entity.getEventType()).isNull();
            assertThat(entity.getPayload()).isNull();
            assertThat(entity.getStatus()).isNull();
        }
    }
}
