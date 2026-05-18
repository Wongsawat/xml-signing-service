package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.infrastructure.client.csc.CSCCredentialsInfoClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CscCredentialInfoCache {

    private final CSCCredentialsInfoClient credentialsInfoClient;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    private volatile String certificate;

    @PostConstruct
    public void init() {
        refresh();
    }

    public String getCertificate() {
        return certificate;
    }

    public void refresh() {
        log.info("Fetching signing certificate from credentials/info for credentialID={}", credentialId);
        CSCCredentialsInfoResponse response = credentialsInfoClient.getCredentialInfo(
            new CSCCredentialsInfoRequest(credentialId)
        );
        String[] certs = response.getCert().getCertificates();
        if (certs == null || certs.length == 0) {
            throw new IllegalStateException(
                "credentials/info returned empty certificate array for credentialID=" + credentialId);
        }
        certificate = certs[0];
        log.info("Cached signing certificate from credentials/info for credentialID={}", credentialId);
    }
}