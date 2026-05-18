package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class CscSignatureAdapter implements CscSignaturePort {

    private final CSCSignatureClient feignClient;

    @Override
    public CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException {
        log.debug("Delegating signHash to CSC API for credentialId={}", command.credentialId());

        CSCSignatureRequest request = CSCSignatureRequest.builder()
                .credentialID(command.credentialId())
                .SAD(command.sadToken())
                .hashAlgorithmOID(command.hashAlgorithmOid())
                .hashes(command.hashes().toArray(new String[0]))
                .build();

        try {
            CSCSignatureResponse response = feignClient.signHash(request);
            validateResponse(response);
            return new CscSignHashResult(
                    Arrays.asList(response.getSignatures()),
                    response.getResponseID()
            );
        } catch (CscSignatureException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSC signHash failed for credentialId={}", command.credentialId(), e);
            throw new CscSignatureException("CSC signHash failed: " + e.getMessage(), e);
        }
    }

    private void validateResponse(CSCSignatureResponse response) {
        if (response.getSignatures() == null || response.getSignatures().length == 0) {
            throw new CscSignatureException("CSC signHash response missing signatures");
        }
    }
}