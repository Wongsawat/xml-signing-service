package com.invoice.xmlsigning.infrastructure.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom error decoder for CSC API errors
 */
@Slf4j
public class CSCErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("CSC API error - Method: {}, Status: {}", methodKey, response.status());

        return switch (response.status()) {
            case 400 -> new IllegalArgumentException("Invalid request to CSC API: " + methodKey);
            case 401 -> new SecurityException("Unauthorized CSC API access: " + methodKey);
            case 403 -> new SecurityException("Forbidden CSC API access: " + methodKey);
            case 404 -> new IllegalStateException("CSC API endpoint not found: " + methodKey);
            case 500, 502, 503, 504 -> new RuntimeException("CSC service unavailable: " + methodKey);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
