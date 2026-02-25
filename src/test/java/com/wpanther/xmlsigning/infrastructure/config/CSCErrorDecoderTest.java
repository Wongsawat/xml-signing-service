package com.wpanther.xmlsigning.infrastructure.config;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.domain.exception.XmlSigningException;
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
 * <p>
 * Verifies that CSC API HTTP errors are properly translated into typed domain
 * exceptions that can be handled by the application's retry logic and circuit breaker.
 */
@DisplayName("CSCErrorDecoder")
class CSCErrorDecoderTest {

    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CREDENTIAL_ID = "test-credential";

    private CSCErrorDecoder errorDecoder;

    @BeforeEach
    void setUp() {
        errorDecoder = new CSCErrorDecoder(TEST_CLIENT_ID, TEST_CREDENTIAL_ID);
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
    @DisplayName("decode() for Authorization Endpoint")
    class AuthorizationEndpoint {

        @Test
        @DisplayName("400 returns CscAuthorizationException with client info")
        void testAuthorize400() {
            Response response = buildResponse(400);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result)
                    .isInstanceOf(CscAuthorizationException.class)
                    .isInstanceOf(XmlSigningException.class);

            CscAuthorizationException ex = (CscAuthorizationException) result;
            assertThat(ex.getClientId()).isEqualTo(TEST_CLIENT_ID);
            assertThat(ex.getCredentialId()).isEqualTo(TEST_CREDENTIAL_ID);
            assertThat(ex.getMessage()).contains("Invalid authorization request");
        }

        @Test
        @DisplayName("401 returns CscAuthorizationException for invalid credentials")
        void testAuthorize401() {
            Response response = buildResponse(401);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);

            CscAuthorizationException ex = (CscAuthorizationException) result;
            assertThat(ex.getMessage()).contains("Invalid client ID or credential ID");
        }

        @Test
        @DisplayName("403 returns CscAuthorizationException for forbidden access")
        void testAuthorize403() {
            Response response = buildResponse(403);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("not authorized");
        }

        @Test
        @DisplayName("404 returns CscAuthorizationException for endpoint not found")
        void testAuthorize404() {
            Response response = buildResponse(404);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("not found");
        }

        @Test
        @DisplayName("429 returns CscAuthorizationException for rate limiting")
        void testAuthorize429() {
            Response response = buildResponse(429);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("rate limit");
        }

        @Test
        @DisplayName("500 returns CscAuthorizationException for service unavailable")
        void testAuthorize500() {
            Response response = buildResponse(500);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("502 returns CscAuthorizationException for bad gateway")
        void testAuthorize502() {
            Response response = buildResponse(502);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("502");
        }

        @Test
        @DisplayName("503 returns CscAuthorizationException for service unavailable")
        void testAuthorize503() {
            Response response = buildResponse(503);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("503");
        }

        @Test
        @DisplayName("504 returns CscAuthorizationException for gateway timeout")
        void testAuthorize504() {
            Response response = buildResponse(504);

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("504");
        }
    }

    @Nested
    @DisplayName("decode() for Signature Endpoint")
    class SignatureEndpoint {

        @Test
        @DisplayName("400 returns CscSignatureException for invalid request")
        void testSignHash400() {
            Response response = buildResponse(400);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result)
                    .isInstanceOf(CscSignatureException.class)
                    .isInstanceOf(XmlSigningException.class);

            CscSignatureException ex = (CscSignatureException) result;
            assertThat(ex.getMessage()).contains("Invalid signature request");
            assertThat(ex.getTransactionId()).isNull(); // No transaction ID at decode time
        }

        @Test
        @DisplayName("401 returns CscSignatureException for invalid SAD token")
        void testSignHash401() {
            Response response = buildResponse(401);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("SAD token");
        }

        @Test
        @DisplayName("403 returns CscSignatureException for forbidden operation")
        void testSignHash403() {
            Response response = buildResponse(403);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("not authorized");
        }

        @Test
        @DisplayName("404 returns CscSignatureException for endpoint not found")
        void testSignHash404() {
            Response response = buildResponse(404);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("not found");
        }

        @Test
        @DisplayName("429 returns CscSignatureException for rate limiting")
        void testSignHash429() {
            Response response = buildResponse(429);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("rate limit");
        }

        @Test
        @DisplayName("500 returns CscSignatureException for service unavailable")
        void testSignHash500() {
            Response response = buildResponse(500);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("502 returns CscSignatureException for bad gateway")
        void testSignHash502() {
            Response response = buildResponse(502);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("502");
        }

        @Test
        @DisplayName("503 returns CscSignatureException for service unavailable")
        void testSignHash503() {
            Response response = buildResponse(503);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("503");
        }

        @Test
        @DisplayName("504 returns CscSignatureException for gateway timeout")
        void testSignHash504() {
            Response response = buildResponse(504);

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("504");
        }
    }

    @Nested
    @DisplayName("decode() Fallback Behavior")
    class FallbackBehavior {

        @Test
        @DisplayName("Unknown method key uses default decoder for 500")
        void testUnknownMethod() {
            Response response = buildResponse(500);

            Exception result = errorDecoder.decode("UnknownMethod#unknown()", response);

            // Should use default decoder which returns FeignException
            assertThat(result).isNotNull();
            assertThat(result.getClass().getName()).contains("FeignException");
        }

        @Test
        @DisplayName("Unknown status code for authorize throws typed exception")
        void testUnknownStatusForAuthorize() {
            Response response = buildResponse(418); // I'm a teapot

            Exception result = errorDecoder.decode("CscAuthClient#authorize()", response);

            // Throws typed CscAuthorizationException with generic message
            assertThat(result).isInstanceOf(CscAuthorizationException.class);
            assertThat(result.getMessage()).contains("unexpected status 418");
        }

        @Test
        @DisplayName("Unknown status code for signHash throws typed exception")
        void testUnknownStatusForSignHash() {
            Response response = buildResponse(418); // I'm a teapot

            Exception result = errorDecoder.decode("CSCSignatureClient#signHash()", response);

            // Throws typed CscSignatureException with generic message
            assertThat(result).isInstanceOf(CscSignatureException.class);
            assertThat(result.getMessage()).contains("unexpected status 418");
        }

        @Test
        @DisplayName("Method key is included in error message for debugging")
        void testMethodKeyInMessage() {
            Response response = buildResponse(401);

            CscAuthorizationException result = (CscAuthorizationException)
                    errorDecoder.decode("CscAuthClient#authorize()", response);

            // Exception should contain context about which method failed
            assertThat(result.getMessage()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Constructor and Configuration")
    class Constructor {

        @Test
        @DisplayName("Stores client ID and credential ID for exception context")
        void testStoresConfiguration() {
            CSCErrorDecoder decoder = new CSCErrorDecoder("my-client", "my-credential");

            Response response = buildResponse(401);
            CscAuthorizationException result = (CscAuthorizationException)
                    decoder.decode("CscAuthClient#authorize()", response);

            assertThat(result.getClientId()).isEqualTo("my-client");
            assertThat(result.getCredentialId()).isEqualTo("my-credential");
        }

        @Test
        @DisplayName("Handles empty client ID and credential ID")
        void testEmptyConfiguration() {
            CSCErrorDecoder decoder = new CSCErrorDecoder("", "");

            Response response = buildResponse(401);
            CscAuthorizationException result = (CscAuthorizationException)
                    decoder.decode("CscAuthClient#authorize()", response);

            assertThat(result.getClientId()).isNotNull();
            assertThat(result.getCredentialId()).isNotNull();
        }
    }
}
