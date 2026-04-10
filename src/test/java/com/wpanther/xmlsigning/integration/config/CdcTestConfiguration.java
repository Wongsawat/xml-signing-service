package com.wpanther.xmlsigning.integration.config;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakersHealthIndicatorAutoConfiguration;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring test configuration for CDC integration tests.
 * <p>
 * Excludes Camel and Kafka auto-configuration (no consumer route needed).
 * Enables JPA + outbox persistence for writing outbox events that Debezium CDC reads.
 * <p>
 * Must be profile-gated to prevent the @EnableAutoConfiguration exclusions
 * from affecting other test contexts (e.g. consumer tests that need Camel).
 */
@Configuration
@Profile("cdc-test")
@EnableAutoConfiguration(exclude = {
    CamelAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    CircuitBreakersHealthIndicatorAutoConfiguration.class
})
@Import(TestKafkaConsumerConfig.class)
@EnableJpaRepositories(basePackages = {
    "com.wpanther.xmlsigning.infrastructure.persistence"
})
@EntityScan(basePackages = {
    "com.wpanther.xmlsigning.infrastructure.persistence"
})
@ComponentScan(
    basePackages = {
        "com.wpanther.xmlsigning.domain",
        "com.wpanther.xmlsigning.application.service",
        "com.wpanther.xmlsigning.infrastructure.persistence",
        "com.wpanther.xmlsigning.infrastructure.messaging",
        "com.wpanther.xmlsigning.infrastructure.config",
        "com.wpanther.xmlsigning.infrastructure.config.feign",
        "com.wpanther.xmlsigning.infrastructure.config.minio",
        "com.wpanther.xmlsigning.infrastructure.config.outbox",
        "com.wpanther.saga.infrastructure"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*CamelConfig.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*RouteConfig.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Controller.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*FeignConfig.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*MinioConfig.*")
    }
)
public class CdcTestConfiguration {
}
