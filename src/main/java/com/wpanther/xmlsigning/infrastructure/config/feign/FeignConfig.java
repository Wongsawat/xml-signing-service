package com.wpanther.xmlsigning.infrastructure.config.feign;

import feign.Logger;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Base Feign client configuration for CSC API v2.0 clients.
 *
 * <p><strong>Logger Level:</strong> Configured via bean to ensure FULL logging for
 * debugging CSC API interactions. This can be overridden in application.yml
 * using {@code spring.cloud.openfeign.client.config.default.loggerLevel}.
 *
 * <p><strong>Error Handling:</strong> CSCErrorDecoder is a {@code @Component} and is
 * auto-wired by Spring for all Feign clients.
 *
 * <p><strong>Circuit Breaker:</strong> Resilience4j configuration is in application.yml
 * under {@code resilience4j.circuitbreaker.instances}.
 *
 * <p><strong>Retry Strategy:</strong> This configuration does NOT include a Retryer bean.
 * The {@code signHash} endpoint is NOT idempotent (HSM signatures are non-deterministic),
 * so Feign-level retries are disabled to prevent duplicate signature generation with
 * consumed SAD tokens. For the {@code authorize} endpoint (which IS idempotent),
 * see {@link AuthFeignConfig} which provides a retrying Retryer.
 *
 * @see AuthFeignConfig for the authorize endpoint (with retry)
 */
@Configuration
public class FeignConfig {

    /**
     * Configures Feign logger level to FULL for detailed request/response logging.
     *
     * @return the logger level
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    // Note: CSCErrorDecoder is now a @Component and is auto-wired by Spring.
    // The Feign clients will use the CSCErrorDecoder bean automatically.

    /**
     * Configures default Resilience4j circuit breaker for all Feign clients.
     *
     * <p>Client-specific configurations can be added in application.yml under
     * {@code resilience4j.circuitbreaker.instances.<client-name>}.
     *
     * @return the circuit breaker customizer
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(30))
                    .build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(5)
                    .build())
                .build());
        };
    }
}
