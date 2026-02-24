package com.wpanther.xmlsigning.domain.port;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;

/**
 * Port for CSC (Cloud Signature Consortium) signature operations.
 * <p>
 * This port defines the contract for signing document hashes using the CSC service.
 * The actual cryptographic signing happens in the RSSP's Hardware Security Module (HSM),
 * providing non-repudiable digital signatures compliant with Thai e-Tax requirements.
 * <p>
 * The signHash operation uses a SAD token obtained from {@link CscAuthorizationPort}
 * for authorization, following CSC API v2.0 best practices.
 * <p>
 * <strong>Important:</strong> SAD tokens are single-use. If signHash fails for any reason
 * (including network errors), the SAD token becomes invalid and a new authorization
 * must be obtained before retrying.
 * <p>
 * <strong>Architecture Note:</strong> This is a domain port interface. The infrastructure
 * layer provides the adapter implementation (e.g., Feign-based HTTP client).
 *
 * @see <a href="https://cloudsignatureconsortium.org/wp-content/uploads/2022/10/CSC-API-v2.0.2-Final.pdf">CSC API v2.0 Specification</a>
 */
public interface CscSignaturePort {

    /**
     * Signs one or more document hashes using the specified credential.
     * <p>
     * This method sends document digests to the CSC service for signing.
     * The RSSP's HSM performs the cryptographic operation and returns the raw
     * signature value(s) along with the X.509 certificate chain.
     * <p>
     * The signature value is then embedded into the XML document as an XAdES-BASELINE-T
     * signature by the {@code XadesSignatureEmbedder}.
     * <p>
     * <strong>Note on SAD Tokens:</strong> The {@code CSCSignatureRequest} must include
     * a valid SAD token obtained from {@link CscAuthorizationPort#authorize}.
     * SAD tokens are single-use - if this method fails, a new authorization is required.
     *
     * @param request the signature request containing SAD token, credential ID,
     *                 hash algorithm, and signature data (digests + attributes)
     * @return the signature response containing raw signature(s) and certificate chain
     * @throws CscSignatureException if signing fails due to invalid SAD token,
     *                                   credential issues, or service unavailability
     */
    CSCSignatureResponse signHash(CSCSignatureRequest request) throws CscSignatureException;
}
