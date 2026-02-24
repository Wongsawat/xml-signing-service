package com.wpanther.xmlsigning.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Feign client configuration with circuit breaker and retry logic
 */
@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(
            10000, TimeUnit.MILLISECONDS,  // connect timeout
            30000, TimeUnit.MILLISECONDS,  // read timeout
            true                            // follow redirects
        );
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100L,    // period (milliseconds)
            1000L,   // max period (milliseconds)
            3        // max attempts
        );
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CSCErrorDecoder();
    }

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

            // Specific configuration for CSC Auth Client
            factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(5)
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(30))
                    .build()),
                "cscAuthClient");

            // Specific configuration for CSC Signature Client
            // Note: Signature failures should NOT be retried (SAD token is single-use)
            factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(60))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(5)
                    .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(30))
                    .build()),
                "cscSignatureClient");
        };
    }
}
