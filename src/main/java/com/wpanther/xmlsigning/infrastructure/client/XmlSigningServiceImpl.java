package com.wpanther.xmlsigning.infrastructure.client;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.domain.port.CscAuthorizationPort;
import com.wpanther.xmlsigning.domain.port.CscSignaturePort;
import com.wpanther.xmlsigning.domain.service.SigningResult;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
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
 * 2. SAD token is obtained via /credentials/authorize endpoint
 * 3. Hash is sent to CSC via signHash with SAD token (not PIN)
 * 4. Raw signature is returned from CSC HSM
 * 5. Signature is embedded locally into XML using Apache Santuario
 * <p>
 * The signHash endpoint uses SAD token-based authentication for security,
 * following CSC API v2.0 best practices. SAD tokens are short-lived (typically 15 min).
 * <p>
 * <strong>Hexagonal Architecture:</strong> This service depends on domain ports
 * ({@link CscAuthorizationPort}, {@link CscSignaturePort}) rather than infrastructure
 * implementations, enabling clean separation and easier testing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XmlSigningServiceImpl implements XmlSigningService {

    private final CscSignaturePort signaturePort;
    private final CscAuthorizationPort authorizationPort;
    private final XadesSignatureEmbedder signatureEmbedder;

    @Value("${app.csc.client-id}")
    private String clientId;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    @Value("${app.csc.hash-algorithm:SHA-256withRSA}")
    private String hashAlgorithm;

    @Value("${app.csc.signature-level:XAdES-BASELINE-T}")
    private String signatureLevel;

    @Value("${app.csc.digest-algorithm:SHA256}")
    private String digestAlgorithm;

    @Override
    public SigningResult signXml(String xmlContent, String documentId) {
        log.info("Starting XML signing process for document: {}", documentId);

        try {
            // Step 1: Compute document digest locally (SHA-256)
            String documentDigest = calculateDigest(xmlContent);
            log.debug("Computed digest for document: {}", documentId);

            // Step 2: Call CSC signHash endpoint with digest and SAD token
            SigningApiResponse apiResponse = signHash(documentDigest);
            log.info("Document signed successfully: {}", documentId);

            // Step 3: Extract raw signature and certificate from response
            String rawSignature = apiResponse.signatureResponse().getSignatures()[0];
            String certificate = apiResponse.signatureResponse().getCertificate();

            // Step 4: Embed signature into XML locally (XAdES-BASELINE-T)
            String signedXml = signatureEmbedder.embedSignature(xmlContent, documentDigest, rawSignature, certificate);

            return new SigningResult(signedXml, certificate, apiResponse.transactionId());

        } catch (CscAuthorizationException | CscSignatureException e) {
            // Re-throw our specific exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to sign XML document: {}", documentId, e);
            throw new CscSignatureException("XML signing failed: " + e.getMessage(), e, null);
        }
    }

    /**
     * Sign the hash using CSC signHash endpoint with SAD token authentication.
     * <p>
     * Only the digest is sent to CSC, not the full document.
     * SAD token is obtained via /credentials/authorize endpoint for each signing operation.
     *
     * @param documentDigest The base64url-encoded SHA-256 digest
     * @return The signing API response containing transaction ID and signature response
     * @throws CscAuthorizationException if CSC authorization fails
     * @throws CscSignatureException if CSC signHash call fails
     */
    private SigningApiResponse signHash(String documentDigest) {
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

        // Step 1: Authorize to get SAD token
        log.debug("Authorizing signing operation with CSC API");
        CSCAuthorizeRequest authRequest = CSCAuthorizeRequest.builder()
            .clientId(clientId)
            .credentialID(credentialId)
            .numSignatures("1")
            .hashAlgo(digestAlgorithm)
            .hash(new String[]{documentDigest})
            .description("Thai e-Tax Invoice XML Signing")
            .build();

        CSCAuthorizeResponse authResponse;
        String transactionId;
        try {
            authResponse = authorizationPort.authorize(authRequest);
            transactionId = authResponse.getTransactionID();
            log.debug("Received SAD token and transaction ID {} from CSC API", transactionId);
        } catch (Exception e) {
            log.error("CSC authorization failed for client {} credential {}",
                    clientId, credentialId, e);
            throw new CscAuthorizationException(
                    "CSC authorization failed: " + e.getMessage(),
                    e,
                    clientId,
                    credentialId
            );
        }

        // Step 2: Build signHash request with SAD (no credentials/PIN)
        CSCSignatureRequest signRequest = CSCSignatureRequest.builder()
            .clientId(clientId)
            .credentialID(credentialId)
            .SAD(authResponse.getSAD())
            .hashAlgo(hashAlgorithm)
            .signatureData(signatureData)
            .build();

        // Step 3: Call signHash endpoint
        try {
            CSCSignatureResponse signatureResponse = signaturePort.signHash(signRequest);
            return new SigningApiResponse(transactionId, signatureResponse);
        } catch (Exception e) {
            log.error("CSC signHash failed for transaction {}", transactionId, e);
            throw new CscSignatureException(
                    "CSC signHash failed: " + e.getMessage(),
                    e,
                    transactionId
            );
        }
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

    /**
     * Internal record to hold the CSC API responses together.
     */
    private record SigningApiResponse(
            String transactionId,
            CSCSignatureResponse signatureResponse
    ) {}
}
