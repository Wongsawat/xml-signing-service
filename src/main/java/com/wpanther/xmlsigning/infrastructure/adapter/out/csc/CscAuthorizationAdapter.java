package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CscAuthorizationAdapter implements CscAuthorizationPort {

    private final CSCAuthClient feignClient;

    @Override
    public CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException {
        log.debug("Delegating authorization to CSC API for credentialId={}", command.credentialId());

        List<CSCAuthorizeRequest.AuthDataEntry> authData = null;
        if (command.pin() != null && !command.pin().isBlank()) {
            authData = List.of(CSCAuthorizeRequest.AuthDataEntry.builder()
                    .id("PIN").value(command.pin()).build());
        }

        CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .credentialID(command.credentialId())
                .numSignatures(1)
                .hashAlgorithmOID(command.hashAlgorithmOid())
                .hashes(command.hashes().toArray(new String[0]))
                .authData(authData)
                .description(command.description())
                .build();

        try {
            CSCAuthorizeResponse response = feignClient.authorize(request);
            validateResponse(response, command.credentialId());
            return new CscAuthorizeResult(response.getSAD());
        } catch (FeignException e) {
            log.error("CSC authorization failed for credentialId={}: {}",
                    command.credentialId(), e.getMessage(), e);
            throw new CscAuthorizationException(
                    "CSC authorization failed: " + e.getMessage(),
                    e, command.credentialId());
        }
    }

    private void validateResponse(CSCAuthorizeResponse response, String credentialId) {
        if (response.getSAD() == null || response.getSAD().isBlank()) {
            throw new CscAuthorizationException(
                    "CSC authorization response missing SAD token",
                    credentialId);
        }
    }
}