package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.*;
import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Implementation of XML signing using CSC API v2.0 signHash endpoint.
 * <p>
 * Following the reference architecture pattern:
 * 1. Hash is computed locally
 * 2. Hash is sent to CSC via signHash (not full document)
 * 3. Raw signature is returned from CSC HSM
 * 4. Signature is embedded locally into XML using Apache Santuario
 * <p>
 * The signHash endpoint uses PIN-based authentication via credentials.pin.value field,
 * NOT SAD tokens (SAD tokens are only for signDocument endpoint).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XmlSigningServiceImpl implements XmlSigningService {

    private final CSCSignatureClient signatureClient;
    private final XadesSignatureEmbedder signatureEmbedder;

    @Value("${app.csc.client-id}")
    private String clientId;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.pin:1234}")
    private String pin;

    @Value("${app.csc.hash-algorithm:SHA-256withRSA}")
    private String hashAlgorithm;

    @Value("${app.csc.signature-level:XAdES-BASELINE-T}")
    private String signatureLevel;

    @Value("${app.csc.digest-algorithm:SHA256}")
    private String digestAlgorithm;

    @Override
    public String signXml(String xmlContent, String documentId) {
        log.info("Starting XML signing process for document: {}", documentId);

        try {
            // Step 1: Compute document digest locally (SHA-256)
            String documentDigest = calculateDigest(xmlContent);
            log.debug("Computed digest for document: {}", documentId);

            // Step 2: Call CSC signHash endpoint with digest and PIN
            CSCSignatureResponse signatureResponse = signHash(documentDigest);
            log.info("Document signed successfully: {}", documentId);

            // Step 3: Extract raw signature and certificate from response
            String rawSignature = signatureResponse.getSignatures()[0];
            String certificate = signatureResponse.getCertificate();

            // Step 4: Embed signature into XML locally (XAdES-BASELINE-T)
            String signedXml = signatureEmbedder.embedSignature(xmlContent, documentDigest, rawSignature, certificate);

            return signedXml;

        } catch (Exception e) {
            log.error("Failed to sign XML document: {}", documentId, e);
            throw new RuntimeException("XML signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sign the hash using CSC signHash endpoint with PIN-based authentication.
     * <p>
     * Only the digest is sent to CSC, not the full document.
     * The signHash endpoint uses credentials.pin.value for authentication, NOT SAD tokens.
     *
     * @param documentDigest The base64-encoded SHA-256 digest
     * @return The signature response with raw signature and certificate
     */
    private CSCSignatureResponse signHash(String documentDigest) {
        // Build signature attributes for XAdES-BASELINE-T
        SignatureAttributes signatureAttributes = SignatureAttributes.builder()
            .signatureType("XAdES")
            .signatureLevel(signatureLevel)
            .signatureForm("enveloped")
            .digestAlgorithm(digestAlgorithm)
            .signDate(System.currentTimeMillis())
            .build();

        // Build signature data with hash to sign
        SignatureData signatureData = SignatureData.builder()
            .hashToSign(new String[]{documentDigest})
            .signatureAttributes(signatureAttributes)
            .build();

        // Build signHash request with PIN-based credentials
        CSCSignatureRequest.Credentials credentials = CSCSignatureRequest.Credentials.builder()
            .pin(CSCSignatureRequest.Pin.builder().value(pin).build())
            .build();

        CSCSignatureRequest signRequest = CSCSignatureRequest.builder()
            .clientId(clientId)
            .credentials(credentials)
            .credentialID(credentialId)
            .hashAlgo(hashAlgorithm)
            .signatureData(signatureData)
            .build();

        // Call signHash endpoint
        return signatureClient.signHash(signRequest);
    }

    /**
     * Calculate SHA-256 digest of XML content.
     * This is computed locally - only the digest is sent to CSC.
     */
    private String calculateDigest(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

        // Encode as base64url (no padding) for CSC API compatibility
        String base64 = Base64.getEncoder().encodeToString(hash);
        return base64.replace("+", "-").replace("/", "_").replaceAll("=+$", "");
    }
}
