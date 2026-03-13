package com.wpanther.xmlsigning.infrastructure.config.feign;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Custom error decoder for CSC API errors.
 * <p>
 * Translates HTTP error responses from the CSC service into typed domain exceptions
 * that can be properly handled by the application's retry logic and circuit breaker.
 * <p>
 * This decoder is a Spring {@link Component} that receives client ID and credential ID
 * via {@link Value} injection, enabling proper domain exception construction with
 * debugging context.
 * <p>
 * Circuit breaker behavior is preserved because Resilience4j catches
 * {@link Exception} broadly, so typed exceptions still trigger failure tracking.
 */
@Component
@Slf4j
public class CSCErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    private final String clientId;
    private final String credentialId;

    /**
     * Constructs a new CSC error decoder with configuration values.
     * <p>
     * Spring automatically injects the configured values, enabling
     * proper domain exception construction with debugging context.
     *
     * @param clientId the configured CSC client ID
     * @param credentialId the configured CSC credential ID
     */
    public CSCErrorDecoder(
            @Value("${app.csc.client-id}") String clientId,
            @Value("${app.csc.credential-id}") String credentialId) {
        this.clientId = clientId;
        this.credentialId = credentialId;
        log.debug("CSCErrorDecoder initialized with clientId={}, credentialId={}", clientId, credentialId);
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.error("CSC API error - Method: {}, Status: {}", methodKey, status);

        // Determine exception type based on HTTP status and method being called
        if (methodKey.contains("authorize")) {
            return decodeAuthorizationError(methodKey, status);
        } else if (methodKey.contains("signHash")) {
            return decodeSignatureError(methodKey, status);
        }

        // Fallback for unknown methods - use default decoder
        Exception defaultException = defaultDecoder.decode(methodKey, response);
        log.warn("CSC API error using default decoder - Method: {}, Status: {}, Exception: {}",
                methodKey, status, defaultException.getClass().getSimpleName());
        return defaultException;
    }

    /**
     * Decodes authorization endpoint errors into {@link CscAuthorizationException}.
     * <p>
     * The authorize endpoint (/csc/v2/credentials/authorize) validates credentials
     * and issues SAD tokens. Errors here typically indicate authentication/authorization
     * failures that should prevent retry without fixing the credential issue.
     *
     * @param methodKey the Feign method key
     * @param status the HTTP status code
     * @return a typed domain exception
     */
    private Exception decodeAuthorizationError(String methodKey, int status) {
        return switch (status) {
            case 400 -> new CscAuthorizationException(
                    "Invalid authorization request to CSC API. Check hash algorithm and digest format.",
                    clientId, credentialId);
            case 401 -> new CscAuthorizationException(
                    "CSC authorization failed: Invalid client ID or credential ID.",
                    clientId, credentialId);
            case 403 -> new CscAuthorizationException(
                    "CSC authorization failed: Credential not authorized for requested operation.",
                    clientId, credentialId);
            case 404 -> new CscAuthorizationException(
                    "CSC authorization endpoint not found. Verify CSC service URL configuration.",
                    clientId, credentialId);
            case 429 -> new CscAuthorizationException(
                    "CSC rate limit exceeded. Please retry after backoff delay.",
                    clientId, credentialId);
            case 500, 502, 503, 504 -> new CscAuthorizationException(
                    String.format("CSC service unavailable (HTTP %d). Retry with exponential backoff.", status),
                    clientId, credentialId);
            default -> {
                // For unknown status codes, throw a generic authorization exception
                yield new CscAuthorizationException(
                        String.format("CSC authorization failed with unexpected status %d", status),
                        clientId, credentialId);
            }
        };
    }

    /**
     * Decodes signature endpoint errors into {@link CscSignatureException}.
     * <p>
     * The signHash endpoint (/csc/v2/signatures/signHash) uses SAD tokens for
     * authorization. Errors here may indicate invalid SAD tokens, hash algorithm
     * mismatches, or transient service issues.
     * <p>
     * Note: SAD tokens are single-use, so signature failures should NOT be retried
     * with the same SAD token. A new authorization must be obtained first.
     *
     * @param methodKey the Feign method key
     * @param status the HTTP status code
     * @return a typed domain exception
     */
    private Exception decodeSignatureError(String methodKey, int status) {
        String transactionId = extractTransactionIdFromMethod(methodKey);

        return switch (status) {
            case 400 -> new CscSignatureException(
                    "Invalid signature request to CSC API. Check SAD token and hash format.",
                    transactionId);
            case 401 -> new CscSignatureException(
                    "CSC signature failed: Invalid or expired SAD token. Obtain new SAD token before retry.",
                    transactionId);
            case 403 -> new CscSignatureException(
                    "CSC signature failed: Credential not authorized for signature operation.",
                    transactionId);
            case 404 -> new CscSignatureException(
                    "CSC signature endpoint not found. Verify CSC service URL configuration.",
                    transactionId);
            case 429 -> new CscSignatureException(
                    "CSC rate limit exceeded. Please retry after backoff delay with new SAD token.",
                    transactionId);
            case 500, 502, 503, 504 -> new CscSignatureException(
                    String.format("CSC service unavailable (HTTP %d). Retry with new SAD token.", status),
                    transactionId);
            default -> {
                // For unknown status codes, throw a generic signature exception
                yield new CscSignatureException(
                        String.format("CSC signature failed with unexpected status %d", status),
                        transactionId);
            }
        };
    }

    /**
     * Extracts a transaction ID hint from the method key for debugging.
     * <p>
     * The actual transaction ID comes from the authorize response, but this
     * provides context in log messages when the authorize itself fails.
     *
     * @param methodKey the Feign method key
     * @return a transaction ID placeholder (always null, real ID comes from response)
     */
    private String extractTransactionIdFromMethod(String methodKey) {
        // Transaction ID is not available at error decode time for signHash
        // It comes from the preceding authorize response
        return null;
    }
}
