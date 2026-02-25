package com.wpanther.xmlsigning.infrastructure.config;

import feign.Logger;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Feign client configuration with circuit breaker and retry logic.
 * <p>
 * <strong>Timeout Configuration:</strong> Connect and read timeouts are configured
 * via {@code spring.cloud.openfeign.client.config.default} in application.yml.
 * The {@code Request.Options} bean has been removed to allow YAML configuration
 * to take precedence.
 * <p>
 * <strong>Logger Level:</strong> Configured via bean to ensure FULL logging for
 * debugging CSC API interactions. This can be overridden in application.yml
 * using {@code spring.cloud.openfeign.client.config.default.loggerLevel}.
 * <p>
 * <strong>Error Handling:</strong> CSCErrorDecoder is a {@code @Component} and is
 * auto-wired by Spring for all Feign clients.
 * <p>
 * <strong>Circuit Breaker:</strong> Resilience4j configuration is in application.yml
 * under {@code resilience4j.circuitbreaker.instances}.
 */
@Configuration
public class FeignConfig {

    /**
     * Configures Feign logger level to FULL for detailed request/response logging.
     * <p>
     * This can be overridden in application.yml via:
     * {@code spring.cloud.openfeign.client.config.default.loggerLevel}
     *
     * @return the logger level
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Configures Feign retryer with exponential backoff.
     * <p>
     * Retry behavior:
     * <ul>
     *   <li>Initial period: 100ms</li>
     *   <li>Maximum period: 1000ms</li>
     *   <li>Maximum attempts: 3</li>
     *   <li>Exponential backoff enabled</li>
     * </ul>
     * <p>
     * Note: For CSC signHash operations, retries should be handled carefully
     * since SAD tokens are single-use. A failed signHash requires obtaining
     * a new SAD token via authorization before retrying.
     *
     * @return the configured retryer
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100L,    // period (milliseconds)
            1000L,   // max period (milliseconds)
            3        // max attempts
        );
    }

    // Note: CSCErrorDecoder is now a @Component and is auto-wired by Spring.
    // The Feign clients will use the CSCErrorDecoder bean automatically.

    /**
     * Configures default Resilience4j circuit breaker for all Feign clients.
     * <p>
     * Client-specific configurations can be added in application.yml under
     * {@code resilience4j.circuitbreaker.instances.<client-name>}.
     *
     * @return the circuit breaker customizer
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> {
            // Default configuration for all circuit breakers
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
            // Note: CSC client-specific configurations (cscAuthClient, cscSignatureClient)
            // use the default configuration above. For client-specific retry behavior,
            // see resilience4j.retry.instances in application.yml.
        };
    }
}
