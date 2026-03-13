package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the CSC API v2.0 signatures/signHash endpoint.
 * <p>
 * This is a raw HTTP client in the infrastructure layer. It is wrapped by
 * {@link com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscSignatureAdapter},
 * which implements the domain port
 * {@link com.wpanther.xmlsigning.application.port.out.CscSignaturePort}.
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
 * @see com.wpanther.xmlsigning.infrastructure.config.FeignConfig
 * @see <a href="https://cloudsignatureconsortium.org/wp-content/uploads/2022/10/CSC-API-v2.0.2-Final.pdf">CSC API v2.0 Specification</a>
 */
@FeignClient(
    name = "cscSignatureClient",
    url = "${app.csc.service-url:http://localhost:9000}",
    path = "/csc/v2/signatures",
    configuration = com.wpanther.xmlsigning.infrastructure.config.FeignConfig.class
)
public interface CSCSignatureClient {

    /**
     * Sign hash(es) with the specified credential.
     * <p>
     * POST /csc/v2/signatures/signHash
     *
     * @param request the signature request containing hashes to sign
     * @return the response with raw signature(s) and certificate
     */
    @PostMapping("/signHash")
    CSCSignatureResponse signHash(@RequestBody CSCSignatureRequest request);
}
