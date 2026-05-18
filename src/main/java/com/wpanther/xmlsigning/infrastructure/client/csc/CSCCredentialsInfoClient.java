package com.wpanther.xmlsigning.infrastructure.client.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "csc-credentials-info-client",
    url = "${app.csc.service-url}"
)
public interface CSCCredentialsInfoClient {

    @PostMapping("/csc/v2/credentials/info")
    CSCCredentialsInfoResponse getCredentialInfo(@RequestBody CSCCredentialsInfoRequest request);
}