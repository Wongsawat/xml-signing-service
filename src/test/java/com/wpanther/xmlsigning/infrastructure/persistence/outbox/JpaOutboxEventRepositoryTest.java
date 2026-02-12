package com.wpanther.xmlsigning.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JpaOutboxEventRepository}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JpaOutboxEventRepository")
class JpaOutboxEventRepositoryTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    private JpaOutboxEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaOutboxEventRepository(springRepository);
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("SignedXmlDocument")
                .aggregateId("inv-001")
                .eventType("XmlSignedEvent")
                .payload("{}")
                .createdAt(Instant.now())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .topic("xml.signed.tax-invoice")
                .build();
    }

    private OutboxEventEntity createEntity() {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("SignedXmlDocument")
                .aggregateId("inv-001")
                .eventType("XmlSignedEvent")
                .payload("{}")
                .createdAt(Instant.now())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .topic("xml.signed.tax-invoice")
                .build();
    }

    @Nested
    @DisplayName("save() Method")
    class SaveMethod {

        @Test
        @DisplayName("Saves event and returns domain object")
        void testSave() {
            // Setup
            OutboxEvent event = createEvent();
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);

            when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(entity);

            // Execute
            OutboxEvent result = repository.save(event);

            // Verify
            assertThat(result).isNotNull();
            assertThat(result.getAggregateType()).isEqualTo(event.getAggregateType());
            assertThat(result.getAggregateId()).isEqualTo(event.getAggregateId());
            verify(springRepository).save(any(OutboxEventEntity.class));
        }
    }

    @Nested
    @DisplayName("findById() Method")
    class FindByIdMethod {

        @Test
        @DisplayName("Returns event when found")
        void testFindByIdFound() {
            // Setup
            UUID id = UUID.randomUUID();
            OutboxEventEntity entity = createEntity();
            entity.setId(id);

            when(springRepository.findById(id)).thenReturn(Optional.of(entity));

            // Execute
            Optional<OutboxEvent> result = repository.findById(id);

            // Verify
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Returns empty when not found")
        void testFindByIdNotFound() {
            // Setup
            UUID id = UUID.randomUUID();
            when(springRepository.findById(id)).thenReturn(Optional.empty());

            // Execute
            Optional<OutboxEvent> result = repository.findById(id);

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPendingEvents() Method")
    class FindPendingEventsMethod {

        @Test
        @DisplayName("Returns list of pending events")
        void testFindPendingEvents() {
            // Setup
            OutboxEventEntity entity1 = createEntity();
            OutboxEventEntity entity2 = createEntity();

            when(springRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                    .thenReturn(List.of(entity1, entity2));

            // Execute
            List<OutboxEvent> result = repository.findPendingEvents(10);

            // Verify
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Returns empty list when no pending events")
        void testFindPendingEventsEmpty() {
            // Setup
            when(springRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            // Execute
            List<OutboxEvent> result = repository.findPendingEvents(10);

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFailedEvents() Method")
    class FindFailedEventsMethod {

        @Test
        @DisplayName("Returns list of failed events")
        void testFindFailedEvents() {
            // Setup
            OutboxEventEntity entity = createEntity();
            entity.setStatus(OutboxStatus.FAILED);

            when(springRepository.findFailedEventsOrderByCreatedAtAsc(any(Pageable.class)))
                    .thenReturn(List.of(entity));

            // Execute
            List<OutboxEvent> result = repository.findFailedEvents(10);

            // Verify
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Returns empty list when no failed events")
        void testFindFailedEventsEmpty() {
            // Setup
            when(springRepository.findFailedEventsOrderByCreatedAtAsc(any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            // Execute
            List<OutboxEvent> result = repository.findFailedEvents(10);

            // Verify
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deletePublishedBefore() Method")
    class DeletePublishedBeforeMethod {

        @Test
        @DisplayName("Deletes and returns count")
        void testDeletePublishedBefore() {
            // Setup
            Instant before = Instant.now().minusSeconds(3600);
            when(springRepository.deletePublishedBefore(before)).thenReturn(5);

            // Execute
            int result = repository.deletePublishedBefore(before);

            // Verify
            assertThat(result).isEqualTo(5);
            verify(springRepository).deletePublishedBefore(before);
        }
    }

    @Nested
    @DisplayName("findByAggregate() Method")
    class FindByAggregateMethod {

        @Test
        @DisplayName("Returns events for aggregate")
        void testFindByAggregate() {
            // Setup
            OutboxEventEntity entity = createEntity();

            when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("SignedXmlDocument", "inv-001"))
                    .thenReturn(List.of(entity));

            // Execute
            List<OutboxEvent> result = repository.findByAggregate("SignedXmlDocument", "inv-001");

            // Verify
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Returns empty list when no events found")
        void testFindByAggregateEmpty() {
            // Setup
            when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("SignedXmlDocument", "inv-001"))
                    .thenReturn(Collections.emptyList());

            // Execute
            List<OutboxEvent> result = repository.findByAggregate("SignedXmlDocument", "inv-001");

            // Verify
            assertThat(result).isEmpty();
        }
    }
}
