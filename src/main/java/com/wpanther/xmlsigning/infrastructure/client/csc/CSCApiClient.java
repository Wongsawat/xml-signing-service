package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API signature endpoints
 */
@FeignClient(
    name = "csc-api-client",
    url = "${app.csc.service-url}"
)
public interface CSCApiClient {

    /**
     * Sign document with XAdES signature
     */
    @PostMapping("/csc/v2/signatures/signDocument")
    CSCSignDocumentResponse signDocument(@RequestBody CSCSignDocumentRequest request);
}
