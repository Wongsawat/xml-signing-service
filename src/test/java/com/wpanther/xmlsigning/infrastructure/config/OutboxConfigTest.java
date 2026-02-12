package com.wpanther.xmlsigning.infrastructure.config;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.xmlsigning.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.wpanther.xmlsigning.infrastructure.persistence.outbox.SpringDataOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link OutboxConfig}.
 */
@DisplayName("OutboxConfig")
class OutboxConfigTest {

    private final OutboxConfig config = new OutboxConfig();

    @Nested
    @DisplayName("Bean Methods")
    class BeanMethods {

        @Test
        @DisplayName("outboxEventRepository returns JpaOutboxEventRepository")
        void testOutboxEventRepository() {
            SpringDataOutboxRepository mockRepo = mock(SpringDataOutboxRepository.class);

            OutboxEventRepository result = config.outboxEventRepository(mockRepo);

            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(JpaOutboxEventRepository.class);
        }

        @Test
        @DisplayName("outboxEventRepository is not null")
        void testBeanNotNull() {
            SpringDataOutboxRepository mockRepo = mock(SpringDataOutboxRepository.class);

            OutboxEventRepository result = config.outboxEventRepository(mockRepo);

            assertThat(result).isNotNull();
        }
    }
}
