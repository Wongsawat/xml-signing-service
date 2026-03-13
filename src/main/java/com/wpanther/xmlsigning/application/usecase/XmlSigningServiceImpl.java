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
import com.wpanther.xmlsigning.application.usecase.SigningResult;
import com.wpanther.xmlsigning.application.usecase.XmlSigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

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
    private final XadesEmbeddingPort xadesEmbeddingPort; // Infrastructure adapter for XAdES embedding

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

            // Step 3: Extract raw signature and certificate from result
            String rawSignature = apiResponse.signHashResult().signatures().get(0);
            String certificate = apiResponse.signHashResult().certificate();

            // Step 4: Embed signature into XML locally (XAdES-BASELINE-T) via port
            byte[] signedXmlBytes = xadesEmbeddingPort.embedSignature(
                    xmlContent.getBytes(StandardCharsets.UTF_8),
                    rawSignature.getBytes(StandardCharsets.UTF_8),
                    certificate,
                    documentId
            );
            String signedXml = new String(signedXmlBytes, StandardCharsets.UTF_8);

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
     * @return The signing API response containing transaction ID and sign-hash result
     * @throws CscAuthorizationException if CSC authorization fails
     * @throws CscSignatureException     if CSC signHash call fails
     */
    private SigningApiResponse signHash(String documentDigest) {
        // Step 1: Authorize to get SAD token via domain port (uses CscAuthorizeCommand)
        log.debug("Authorizing signing operation with CSC API");
        CscAuthorizeCommand authCommand = new CscAuthorizeCommand(
                clientId, credentialId, "1", digestAlgorithm,
                List.of(documentDigest), "Thai e-Tax Invoice XML Signing");

        CscAuthorizeResult authResult;
        String transactionId;
        try {
            authResult = authorizationPort.authorize(authCommand);
            transactionId = authResult.transactionId();
            log.debug("Received SAD token and transaction ID {} from CSC API", transactionId);
        } catch (CscAuthorizationException e) {
            throw e;  // already the correct domain exception, don't re-wrap
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

        // Step 2: Build CscSignHashCommand with SAD token and signature attributes
        CscSignHashCommand signCommand = new CscSignHashCommand(
                clientId,
                credentialId,
                authResult.sadToken(),
                hashAlgorithm,
                List.of(documentDigest),
                "XAdES",
                signatureLevel,
                "enveloped",
                digestAlgorithm,
                System.currentTimeMillis()
        );

        // Step 3: Call signHash port
        try {
            CscSignHashResult signHashResult = signaturePort.signHash(signCommand);
            return new SigningApiResponse(transactionId, signHashResult);
        } catch (CscSignatureException e) {
            throw e;  // already the correct domain exception, don't re-wrap
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
            CscSignHashResult signHashResult
    ) {}
}
