package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureAttributes;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Infrastructure adapter that implements {@link CscSignaturePort} using a
 * Feign HTTP client ({@link CSCSignatureClient}) to communicate with the CSC API.
 *
 * <p>This class is the Anti-Corruption Layer between the domain port and the
 * infrastructure DTO types. It maps:
 * <ul>
 *   <li>{@link CscSignHashCommand} → {@link CSCSignatureRequest} (note field name
 *       differences: {@code credentialId} → {@code credentialID}, {@code sadToken} → {@code SAD},
 *       {@code hashAlgorithm} → {@code hashAlgo}, {@code documentDigests} {@code List<String>}
 *       → {@code String[]})</li>
 *   <li>{@link CSCSignatureResponse} → {@link CscSignHashResult} ({@code String[]} →
 *       {@code List<String>})</li>
 * </ul>
 *
 * <p>Exception handling: existing {@link CscSignatureException}s are re-thrown as-is to
 * avoid double-wrapping. All other exceptions are wrapped in a new
 * {@link CscSignatureException}.
 *
 * @see CscSignaturePort
 * @see CSCSignatureClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CscSignatureAdapter implements CscSignaturePort {

    private final CSCSignatureClient feignClient;

    @Override
    public CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException {
        log.debug("Delegating signHash to CSC API for clientId={} credentialId={}",
                command.clientId(), command.credentialId());

        SignatureAttributes signatureAttributes = SignatureAttributes.builder()
                .signatureType(command.signatureType())
                .signatureLevel(command.signatureLevel())
                .signatureForm(command.signatureForm())
                .digestAlgorithm(command.digestAlgorithm())
                .signDate(command.signDate())
                .build();

        SignatureData signatureData = SignatureData.builder()
                .hashToSign(command.documentDigests().toArray(new String[0])) // List<String> → String[]
                .signatureAttributes(signatureAttributes)
                .build();

        CSCSignatureRequest request = CSCSignatureRequest.builder()
                .clientId(command.clientId())
                .credentialID(command.credentialId())          // domain: credentialId → feign: credentialID
                .SAD(command.sadToken())                       // domain: sadToken → feign: SAD
                .hashAlgo(command.hashAlgorithm())             // domain: hashAlgorithm → feign: hashAlgo
                .signatureData(signatureData)
                .build();

        try {
            CSCSignatureResponse response = feignClient.signHash(request);
            return new CscSignHashResult(
                    Arrays.asList(response.getSignatures()),   // String[] → List<String>
                    response.getCertificate()
            );
        } catch (CscSignatureException e) {
            throw e;  // already the correct domain exception, don't re-wrap
        } catch (Exception e) {
            log.error("CSC signHash failed for clientId={} credentialId={}",
                    command.clientId(), command.credentialId(), e);
            throw new CscSignatureException("CSC signHash failed: " + e.getMessage(), e);
        }
    }
}
