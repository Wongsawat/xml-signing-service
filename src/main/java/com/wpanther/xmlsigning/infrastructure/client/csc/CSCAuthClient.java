package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API authorization endpoints
 */
@FeignClient(
    name = "csc-auth-client",
    url = "${app.csc.service-url}"
)
public interface CSCAuthClient {

    /**
     * Authorize credential for signing
     */
    @PostMapping("/csc/v2/credentials/authorize")
    CSCAuthorizeResponse authorize(@RequestBody CSCAuthorizeRequest request);
}
