package com.wpanther.xmlsigning.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FeignConfig}.
 */
@DisplayName("FeignConfig")
class FeignConfigTest {

    private final FeignConfig config = new FeignConfig();

    @Nested
    @DisplayName("Bean Methods")
    class BeanMethods {

        @Test
        @DisplayName("feignLoggerLevel returns FULL")
        void testFeignLoggerLevel() {
            Logger.Level level = config.feignLoggerLevel();

            assertThat(level).isEqualTo(Logger.Level.FULL);
        }

        @Test
        @DisplayName("options returns configured Request.Options")
        void testOptions() {
            Request.Options options = config.options();

            assertThat(options).isNotNull();
            assertThat(options.connectTimeout()).isEqualTo(10000);
            assertThat(options.readTimeout()).isEqualTo(30000);
        }

        @Test
        @DisplayName("retryer returns configured Retryer")
        void testRetryer() {
            Retryer retryer = config.retryer();

            assertThat(retryer).isNotNull();
            assertThat(retryer).isInstanceOf(Retryer.Default.class);
        }

        // Note: CSCErrorDecoder is now a @Component and tested separately in CSCErrorDecoderTest
        // The errorDecoder() bean method was removed from FeignConfig

        @Test
        @DisplayName("defaultCustomizer returns non-null Customizer")
        void testDefaultCustomizer() {
            Customizer<Resilience4JCircuitBreakerFactory> customizer = config.defaultCustomizer();

            assertThat(customizer).isNotNull();
        }

        @Test
        @DisplayName("defaultCustomizer configures factory with real factory and creates circuit breaker")
        void testDefaultCustomizerAppliesConfig() {
            Customizer<Resilience4JCircuitBreakerFactory> customizer = config.defaultCustomizer();

            // Create a real factory with real registries
            CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
            TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
            Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(
                    circuitBreakerRegistry, timeLimiterRegistry, null);

            // Apply the customizer
            customizer.customize(factory);

            // Create a circuit breaker - this will invoke the configuration function
            var circuitBreaker = factory.create("test-id");

            assertThat(circuitBreaker).isNotNull();
            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("defaultCustomizer creates multiple circuit breakers")
        void testDefaultCustomizerMultipleCircuitBreakers() {
            Customizer<Resilience4JCircuitBreakerFactory> customizer = config.defaultCustomizer();

            CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
            TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
            Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(
                    circuitBreakerRegistry, timeLimiterRegistry, null);

            customizer.customize(factory);

            // Create multiple circuit breakers to exercise the configuration
            var cb1 = factory.create("test-1");
            var cb2 = factory.create("test-2");

            assertThat(cb1).isNotNull();
            assertThat(cb2).isNotNull();
        }
    }

    @Nested
    @DisplayName("Retry Configuration")
    class RetryConfigTests {

        @Test
        @DisplayName("Retryer is instance of Default")
        void testRetryerIsDefault() {
            Retryer retryer = config.retryer();

            assertThat(retryer).isInstanceOf(Retryer.Default.class);
        }
    }
}
