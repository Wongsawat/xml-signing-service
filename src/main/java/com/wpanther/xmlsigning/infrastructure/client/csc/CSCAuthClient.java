package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API authorization endpoints.
 * <p>
 * This is a low-level HTTP client that speaks the CSC API wire format.
 * It is used exclusively by {@link com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscAuthorizationAdapter},
 * which maps between domain types and these infrastructure DTOs.
 * <p>
 * The Feign client is configured with:
 * <ul>
 *   <li>Circuit breaker (Resilience4j) for fault tolerance</li>
 *   <li>Retry logic with exponential backoff (via {@link AuthFeignConfig})</li>
 *   <li>Custom error decoder for CSC-specific error handling</li>
 *   <li>Full request/response logging for debugging</li>
 * </ul>
 *
 * <p><strong>Retry Note:</strong> The authorize endpoint is idempotent, so retries
 * are safe and enabled via {@link AuthFeignConfig}.
 *
 * @see com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscAuthorizationAdapter
 * @see com.wpanther.xmlsigning.infrastructure.config.feign.AuthFeignConfig
 */
@FeignClient(
    name = "csc-auth-client",
    url = "${app.csc.service-url}",
    configuration = com.wpanther.xmlsigning.infrastructure.config.feign.AuthFeignConfig.class
)
public interface CSCAuthClient {

    /**
     * Authorize credential for signing.
     * <p>
     * POST /csc/v2/credentials/authorize
     *
     * @param request the authorization request
     * @return the authorization response with SAD token
     * @throws CscAuthorizationException propagated from circuit breaker/error decoder
     */
    @PostMapping("/csc/v2/credentials/authorize")
    CSCAuthorizeResponse authorize(@RequestBody CSCAuthorizeRequest request) throws CscAuthorizationException;
}
