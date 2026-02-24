package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.domain.port.CscSignaturePort;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client adapter for CSC API v2.0 signatures/signHash endpoint.
 * <p>
 * This is an infrastructure adapter that implements the {@link CscSignaturePort}
 * domain port interface using Spring Cloud OpenFeign for HTTP communication.
 * <p>
 * The signHash endpoint signs one or more document hashes using the specified
 * credential. The actual cryptographic signing happens in the RSSP's HSM.
 * <p>
 * The Feign client is configured with:
 * <ul>
 *   <li>Circuit breaker (Resilience4j) for fault tolerance</li>
 *   <li>Limited retry logic (SAD tokens are single-use)</li>
 *   <li>Custom error decoder for CSC-specific error handling</li>
 *   <li>Full request/response logging for debugging</li>
 * </ul>
 *
 * @see CscSignaturePort
 * @see com.wpanther.xmlsigning.infrastructure.config.FeignConfig
 * @see <a href="https://cloudsignatureconsortium.org/wp-content/uploads/2022/10/CSC-API-v2.0.2-Final.pdf">CSC API v2.0 Specification</a>
 */
@FeignClient(
    name = "cscSignatureClient",
    url = "${app.csc.service-url:http://localhost:9000}",
    path = "/csc/v2/signatures",
    configuration = com.wpanther.xmlsigning.infrastructure.config.FeignConfig.class
)
public interface CSCSignatureClient extends CscSignaturePort {

    /**
     * Sign hash(es) with the specified credential.
     * <p>
     * POST /csc/v2/signatures/signHash
     *
     * @param request the signature request containing hashes to sign
     * @return the response with raw signature(s) and certificate
     * @throws CscSignatureException propagated from circuit breaker/error decoder
     */
    @PostMapping("/signHash")
    @Override
    CSCSignatureResponse signHash(@RequestBody CSCSignatureRequest request) throws CscSignatureException;
}
