package com.wpanther.xmlsigning.infrastructure.config;

import com.wpanther.xmlsigning.application.service.XmlSigningOrchestrationService;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link XmlSigningRouteConfig}.
 */
@DisplayName("XmlSigningRouteConfig")
class XmlSigningRouteConfigTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor accepts orchestration service")
        void testConstructor() {
            XmlSigningOrchestrationService mockService = org.mockito.Mockito.mock(XmlSigningOrchestrationService.class);

            XmlSigningRouteConfig config = new XmlSigningRouteConfig(mockService);

            assertThat(config).isNotNull();
            assertThat(ReflectionTestUtils.getField(config, "orchestrationService")).isEqualTo(mockService);
        }
    }

    @Nested
    @DisplayName("configure() Method")
    class ConfigureMethod {

        @Test
        @DisplayName("configure() does not throw with valid configuration")
        void testConfigureNoException() {
            XmlSigningOrchestrationService mockService = org.mockito.Mockito.mock(XmlSigningOrchestrationService.class);
            XmlSigningRouteConfig config = new XmlSigningRouteConfig(mockService);

            ReflectionTestUtils.setField(config, "kafkaBrokers", "localhost:9092");
            ReflectionTestUtils.setField(config, "inputTopic", "xml.signing.requested");
            ReflectionTestUtils.setField(config, "dlqTopic", "xml.signing.dlq");

            // configure() should not throw when fields are set
            assertThatNoException().isThrownBy(() -> config.configure());
        }

        @Test
        @DisplayName("configure() creates route definitions")
        void testConfigureCreatesRoutes() throws Exception {
            XmlSigningOrchestrationService mockService = org.mockito.Mockito.mock(XmlSigningOrchestrationService.class);
            XmlSigningRouteConfig config = new XmlSigningRouteConfig(mockService);

            ReflectionTestUtils.setField(config, "kafkaBrokers", "localhost:9092");
            ReflectionTestUtils.setField(config, "inputTopic", "xml.signing.requested");
            ReflectionTestUtils.setField(config, "dlqTopic", "xml.signing.dlq");

            config.configure();

            // Verify routes are defined
            List<RouteDefinition> routes = config.getRouteCollection().getRoutes();
            assertThat(routes).isNotEmpty();
        }

        @Test
        @DisplayName("configure() creates consumer route with correct id")
        void testConfigureCreatesConsumerRoute() throws Exception {
            XmlSigningOrchestrationService mockService = org.mockito.Mockito.mock(XmlSigningOrchestrationService.class);
            XmlSigningRouteConfig config = new XmlSigningRouteConfig(mockService);

            ReflectionTestUtils.setField(config, "kafkaBrokers", "localhost:9092");
            ReflectionTestUtils.setField(config, "inputTopic", "xml.signing.requested");
            ReflectionTestUtils.setField(config, "dlqTopic", "xml.signing.dlq");

            config.configure();

            List<RouteDefinition> routes = config.getRouteCollection().getRoutes();
            assertThat(routes.stream().anyMatch(r -> "xml-signing-consumer".equals(r.getRouteId()))).isTrue();
        }
    }

    @Nested
    @DisplayName("Properties")
    class PropertiesTests {

        @Test
        @DisplayName("Route extends RouteBuilder")
        void testExtendsRouteBuilder() {
            assertThat(RouteBuilder.class).isAssignableFrom(XmlSigningRouteConfig.class);
        }
    }
}
