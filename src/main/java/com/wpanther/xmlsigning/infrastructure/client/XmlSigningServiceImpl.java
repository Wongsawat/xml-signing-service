package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Implementation of XML signing using CSC API v2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XmlSigningServiceImpl implements XmlSigningService {

    private final CSCAuthClient authClient;
    private final CSCApiClient apiClient;

    @Value("${app.csc.client-id}")
    private String clientId;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.hash-algorithm:SHA256}")
    private String hashAlgorithm;

    @Value("${app.csc.signature-level:XAdES-BASELINE-T}")
    private String signatureLevel;

    @Value("${app.csc.digest-algorithm:SHA256}")
    private String digestAlgorithm;

    @Override
    public String signXml(String xmlContent, String documentId) {
        log.info("Starting XML signing process for document: {}", documentId);

        try {
            // Step 1: Authorize credential and get SAD token
            CSCAuthorizeResponse authResponse = authorizeCredential();
            log.debug("Authorization successful, SAD token received for document: {}", documentId);

            // Step 2: Encode XML to Base64
            String encodedXml = Base64.getEncoder().encodeToString(
                xmlContent.getBytes(StandardCharsets.UTF_8));

            // Step 3: Calculate document digest
            String documentDigest = calculateDigest(xmlContent);

            // Step 4: Build signature attributes for XAdES-BASELINE-T
            SignatureAttributes signatureAttributes = SignatureAttributes.builder()
                .signatureType("XAdES")
                .signatureLevel(signatureLevel)
                .signatureForm("enveloped")
                .digestAlgorithm(digestAlgorithm)
                .signDate(System.currentTimeMillis())
                .build();

            // Step 5: Sign document
            CSCSignDocumentRequest signRequest = CSCSignDocumentRequest.builder()
                .clientId(clientId)
                .credentialID(credentialId)
                .SAD(authResponse.getSAD())
                .documentID(documentId)
                .document(encodedXml)
                .documentDigest(documentDigest)
                .hashAlgo(hashAlgorithm)
                .signatureAttributes(signatureAttributes)
                .build();

            CSCSignDocumentResponse signResponse = apiClient.signDocument(signRequest);
            log.info("Document signed successfully: {}", documentId);

            // Step 6: Decode signed XML from Base64
            String signedXml = new String(
                Base64.getDecoder().decode(signResponse.getSignedDocument()),
                StandardCharsets.UTF_8);

            return signedXml;

        } catch (Exception e) {
            log.error("Failed to sign XML document: {}", documentId, e);
            throw new RuntimeException("XML signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Authorize credential and get SAD token
     */
    private CSCAuthorizeResponse authorizeCredential() {
        CSCAuthorizeRequest authRequest = CSCAuthorizeRequest.builder()
            .clientId(clientId)
            .credentialID(credentialId)
            .numSignatures("1")
            .hashAlgo(hashAlgorithm)
            .validityPeriod(300L) // 5 minutes
            .description("Thai e-Tax Invoice XML Signing")
            .build();

        return authClient.authorize(authRequest);
    }

    /**
     * Calculate SHA-256 digest of XML content
     */
    private String calculateDigest(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
