package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API v2.0 signatures/signHash endpoint.
 * <p>
 * This endpoint signs one or more hashes using the specified credential.
 * The actual cryptographic signing happens in the RSSP's HSM.
 *
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
