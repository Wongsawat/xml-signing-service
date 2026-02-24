package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignDocumentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for CSC API signature endpoints.
 *
 * @deprecated As of 1.1.0, use {@link com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient}
 *             with the signHash endpoint instead. The signDocument endpoint requires
 *             uploading the full document, while signHash only requires the digest.
 *             This client will be removed in version 2.0.0.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@FeignClient(
    name = "csc-api-client",
    url = "${app.csc.service-url}"
)
public interface CSCApiClient {

    /**
     * Sign document with XAdES signature.
     *
     * @deprecated Use {@link com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient#signHash}
     *             instead. The signDocument endpoint requires full document upload
     *             and is less efficient than the signHash approach.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    @PostMapping("/csc/v2/signatures/signDocument")
    CSCSignDocumentResponse signDocument(@RequestBody CSCSignDocumentRequest request);
}
