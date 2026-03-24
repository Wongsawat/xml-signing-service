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

/**
 * Infrastructure adapter that implements {@link CscAuthorizationPort} using a
 * Feign HTTP client ({@link CSCAuthClient}) to communicate with the CSC API.
 *
 * <p>This class is the Anti-Corruption Layer between the domain port and the
 * infrastructure DTO types. It maps:
 * <ul>
 *   <li>{@link CscAuthorizeCommand} → {@link CSCAuthorizeRequest} (note field name differences:
 *       {@code hashAlgorithm} → {@code hashAlgo}, {@code documentDigests} → {@code hash})</li>
 *   <li>{@link CSCAuthorizeResponse} → {@link CscAuthorizeResult}</li>
 * </ul>
 *
 * @see CscAuthorizationPort
 * @see CSCAuthClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CscAuthorizationAdapter implements CscAuthorizationPort {

    private final CSCAuthClient feignClient;

    @Override
    public CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException {
        log.debug("Delegating authorization to CSC API for clientId={} credentialId={}",
                command.clientId(), command.credentialId());

        CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                .clientId(command.clientId())
                .credentialID(command.credentialId())           // domain: credentialId → feign: credentialID
                .numSignatures(command.numSignatures())
                .hashAlgo(command.hashAlgorithm())             // domain: hashAlgorithm → feign: hashAlgo
                .hash(command.documentDigests().toArray(new String[0])) // List<String> → String[]
                .description(command.description())
                .build();

        try {
            CSCAuthorizeResponse response = feignClient.authorize(request);
            validateResponse(response, command.clientId(), command.credentialId());
            return new CscAuthorizeResult(response.getSAD(), response.getTransactionID());
        } catch (FeignException e) {
            log.error("CSC authorization failed for clientId={} credentialId={}: {}",
                    command.clientId(), command.credentialId(), e.getMessage(), e);
            throw new CscAuthorizationException(
                    "CSC authorization failed: " + e.getMessage(),
                    e,
                    command.clientId(),
                    command.credentialId());
        }
    }

    private void validateResponse(CSCAuthorizeResponse response, String clientId, String credentialId) {
        if (response.getSAD() == null || response.getSAD().isBlank()) {
            throw new CscAuthorizationException(
                    "CSC authorization response missing SAD token",
                    clientId, credentialId);
        }
        if (response.getTransactionID() == null || response.getTransactionID().isBlank()) {
            throw new CscAuthorizationException(
                    "CSC authorization response missing transaction ID",
                    clientId, credentialId);
        }
    }
}
