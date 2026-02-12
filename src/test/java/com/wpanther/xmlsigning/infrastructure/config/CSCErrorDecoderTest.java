package com.wpanther.xmlsigning.infrastructure.config;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CSCErrorDecoder}.
 */
@DisplayName("CSCErrorDecoder")
class CSCErrorDecoderTest {

    private CSCErrorDecoder errorDecoder;

    @BeforeEach
    void setUp() {
        errorDecoder = new CSCErrorDecoder();
    }

    private Response buildResponse(int status) {
        return Response.builder()
                .status(status)
                .reason("Test reason")
                .request(Request.create(
                        Request.HttpMethod.POST,
                        "/test",
                        Map.of(),
                        "test".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8,
                        null
                ))
                .headers(Collections.emptyMap())
                .build();
    }

    @Nested
    @DisplayName("decode() Method")
    class DecodeMethod {

        @Test
        @DisplayName("400 returns IllegalArgumentException")
        void testDecode400() {
            Response response = buildResponse(400);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(IllegalArgumentException.class);
            assertThat(result.getMessage()).contains("Invalid request");
        }

        @Test
        @DisplayName("401 returns SecurityException")
        void testDecode401() {
            Response response = buildResponse(401);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(SecurityException.class);
            assertThat(result.getMessage()).contains("Unauthorized");
        }

        @Test
        @DisplayName("403 returns SecurityException")
        void testDecode403() {
            Response response = buildResponse(403);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(SecurityException.class);
            assertThat(result.getMessage()).contains("Forbidden");
        }

        @Test
        @DisplayName("404 returns IllegalStateException")
        void testDecode404() {
            Response response = buildResponse(404);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(IllegalStateException.class);
            assertThat(result.getMessage()).contains("not found");
        }

        @Test
        @DisplayName("500 returns RuntimeException")
        void testDecode500() {
            Response response = buildResponse(500);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("502 returns RuntimeException")
        void testDecode502() {
            Response response = buildResponse(502);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("503 returns RuntimeException")
        void testDecode503() {
            Response response = buildResponse(503);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("504 returns RuntimeException")
        void testDecode504() {
            Response response = buildResponse(504);

            Exception result = errorDecoder.decode("testMethod", response);

            assertThat(result).isInstanceOf(RuntimeException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("Unknown status returns default decoder result")
        void testDecodeUnknown() {
            Response response = buildResponse(418); // I'm a teapot

            Exception result = errorDecoder.decode("testMethod", response);

            // Default decoder throws FeignException
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Includes method key in error message")
        void testMethodKeyInMessage() {
            Response response = buildResponse(400);

            Exception result = errorDecoder.decode("mySpecialMethod", response);

            assertThat(result.getMessage()).contains("mySpecialMethod");
        }
    }
}
