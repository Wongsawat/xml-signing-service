package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.domain.port.CscAuthorizationPort;
import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client adapter for CSC API authorization endpoints.
 * <p>
 * This is an infrastructure adapter that implements the {@link CscAuthorizationPort}
 * domain port interface using Spring Cloud OpenFeign for HTTP communication.
 * <p>
 * The Feign client is configured with:
 * <ul>
 *   <li>Circuit breaker (Resilience4j) for fault tolerance</li>
 *   <li>Retry logic with exponential backoff</li>
 *   <li>Custom error decoder for CSC-specific error handling</li>
 *   <li>Full request/response logging for debugging</li>
 * </ul>
 *
 * @see CscAuthorizationPort
 * @see com.wpanther.xmlsigning.infrastructure.config.FeignConfig
 */
@FeignClient(
    name = "csc-auth-client",
    url = "${app.csc.service-url}",
    configuration = com.wpanther.xmlsigning.infrastructure.config.FeignConfig.class
)
public interface CSCAuthClient extends CscAuthorizationPort {

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
    @Override
    CSCAuthorizeResponse authorize(@RequestBody CSCAuthorizeRequest request) throws CscAuthorizationException;
}
