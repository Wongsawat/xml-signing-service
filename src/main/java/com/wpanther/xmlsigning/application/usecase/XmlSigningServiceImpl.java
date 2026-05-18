package com.wpanther.xmlsigning.application.usecase;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscCredentialInfoCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class XmlSigningServiceImpl implements XmlSigningService {

    private final CscSignaturePort signaturePort;
    private final CscAuthorizationPort authorizationPort;
    private final XadesEmbeddingPort xadesEmbeddingPort;
    private final CscCredentialInfoCache credentialInfoCache;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.hash-algorithm-oid:2.16.840.1.101.3.4.2.1}")
    private String hashAlgorithmOid;

    @Value("${app.csc.digest-algorithm:SHA-256}")
    private String digestAlgorithm;

    @Value("${app.csc.pin:}")
    private String pin;

    @Override
    public SigningResult signXml(String xmlContent, String documentId) {
        log.info("Starting XML signing process for document: {}", documentId);

        try {
            String certificate = credentialInfoCache.getCertificate();
            String documentDigest = calculateDigest(xmlContent);
            log.debug("Computed digest for document: {}", documentId);

            CscAuthorizeResult authResult;
            try {
                CscAuthorizeCommand authCommand = new CscAuthorizeCommand(
                        credentialId, hashAlgorithmOid,
                        List.of(documentDigest),
                        (pin != null && !pin.isBlank()) ? pin : null,
                        "Thai e-Tax Invoice XML Signing");
                authResult = authorizationPort.authorize(authCommand);
                log.debug("Received SAD token from CSC API for document: {}", documentId);
            } catch (CscAuthorizationException e) {
                throw e;
            } catch (Exception e) {
                log.error("CSC authorization failed for credential {} document {}",
                        credentialId, documentId, e);
                throw new CscAuthorizationException(
                        "CSC authorization failed: " + e.getMessage(), e, null, credentialId);
            }

            CscSignHashResult signResult;
            try {
                CscSignHashCommand signCommand = new CscSignHashCommand(
                        credentialId, authResult.sadToken(),
                        hashAlgorithmOid, List.of(documentDigest));
                signResult = signaturePort.signHash(signCommand);
            } catch (CscSignatureException e) {
                throw e;
            } catch (Exception e) {
                log.error("CSC signHash failed for document {}", documentId, e);
                throw new CscSignatureException("CSC signHash failed: " + e.getMessage(), e, null);
            }

            String rawSignature = signResult.signatures().get(0);
            byte[] signedXmlBytes = xadesEmbeddingPort.embedSignature(
                    xmlContent.getBytes(StandardCharsets.UTF_8),
                    rawSignature.getBytes(StandardCharsets.UTF_8),
                    documentDigest,
                    certificate,
                    documentId
            );
            String signedXml = new String(signedXmlBytes, StandardCharsets.UTF_8);

            log.info("Document signed successfully: {}", documentId);
            return new SigningResult(signedXml, certificate, signResult.responseId());

        } catch (CscAuthorizationException | CscSignatureException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sign XML document: {}", documentId, e);
            throw new CscSignatureException("XML signing failed: " + e.getMessage(), e, null);
        }
    }

    private String calculateDigest(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}