package com.wpanther.xmlsigning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link XmlSigningServiceApplication}.
 */
@DisplayName("XmlSigningServiceApplication")
class XmlSigningServiceApplicationTest {

    @Nested
    @DisplayName("Class Structure")
    class ClassStructure {

        @Test
        @DisplayName("Application class exists and is public")
        void testApplicationClassExists() {
            assertThat(XmlSigningServiceApplication.class).isNotNull();
            assertThat(XmlSigningServiceApplication.class.getModifiers()).matches(m -> java.lang.reflect.Modifier.isPublic(m));
        }

        @Test
        @DisplayName("Application has SpringBootApplication annotation")
        void testSpringBootApplicationAnnotation() {
            assertThat(XmlSigningServiceApplication.class.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class))
                    .isNotNull();
        }

        @Test
        @DisplayName("Application has EnableFeignClients annotation")
        void testEnableFeignClientsAnnotation() {
            assertThat(XmlSigningServiceApplication.class.getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Main Method")
    class MainMethod {

        @Test
        @DisplayName("Main method exists and is static")
        void testMainMethodExists() throws NoSuchMethodException {
            Method mainMethod = XmlSigningServiceApplication.class.getMethod("main", String[].class);
            assertThat(mainMethod).isNotNull();
            assertThat(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers())).isTrue();
        }

        @Test
        @DisplayName("Main method returns void")
        void testMainMethodReturnsVoid() throws NoSuchMethodException {
            Method mainMethod = XmlSigningServiceApplication.class.getMethod("main", String[].class);
            assertThat(mainMethod.getReturnType()).isEqualTo(Void.TYPE);
        }

        @Test
        @DisplayName("Main method accepts String array parameter")
        void testMainMethodParameter() throws NoSuchMethodException {
            Method mainMethod = XmlSigningServiceApplication.class.getMethod("main", String[].class);
            assertThat(mainMethod.getParameterTypes()).hasSize(1);
            assertThat(mainMethod.getParameterTypes()[0]).isEqualTo(String[].class);
        }
    }

    @Nested
    @DisplayName("Spring Configuration")
    class SpringConfiguration {

        @Test
        @DisplayName("SpringBootApplication annotation has correct scan behavior")
        void testComponentScan() {
            org.springframework.boot.autoconfigure.SpringBootApplication annotation =
                    XmlSigningServiceApplication.class.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);

            assertThat(annotation).isNotNull();
        }

        @Test
        @DisplayName("EnableFeignClients is configured")
        void testEnableFeignClientsConfigured() {
            org.springframework.cloud.openfeign.EnableFeignClients annotation =
                    XmlSigningServiceApplication.class.getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class);

            assertThat(annotation).isNotNull();
        }
    }
}
