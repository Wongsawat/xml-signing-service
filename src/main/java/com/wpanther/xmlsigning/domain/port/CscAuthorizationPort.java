package com.wpanther.xmlsigning.domain.port;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;

/**
 * Port for CSC (Cloud Signature Consortium) authorization operations.
 * <p>
 * This port defines the contract for obtaining a SAD (Signature Activation Data) token
 * from the CSC service, which is required for subsequent signing operations.
 * <p>
 * The SAD token-based authentication follows CSC API v2.0 best practices and provides
 * better security than deprecated PIN-based authentication.
 * <p>
 * <strong>Architecture Note:</strong> This is a domain port interface. The infrastructure
 * layer provides the adapter implementation (e.g., Feign-based HTTP client).
 *
 * @see <a href="https://cloudsignatureconsortium.org/wp-content/uploads/2022/10/CSC-API-v2.0.2-Final.pdf">CSC API v2.0 Specification</a>
 */
public interface CscAuthorizationPort {

    /**
     * Authorizes a credential for signing operations.
     * <p>
     * This method obtains a SAD token from the CSC service. The SAD token is
     * short-lived (typically 15 minutes) and single-use, ensuring secure delegation
     * of signing authority to the Remote Signing Service Provider (RSSP).
     * <p>
     * The authorize endpoint validates:
     * <ul>
     *   <li>Client ID and credential ID are valid and active</li>
     *   <li>The requested number of signatures is authorized</li>
     *   <li>The hash algorithm matches the credential's capabilities</li>
     * </ul>
     *
     * @param request the authorization request containing client ID, credential ID,
     *                 hash algorithm, and document digests
     * @return the authorization response containing the SAD token and transaction ID
     * @throws CscAuthorizationException if authorization fails due to invalid credentials,
     *                                      insufficient permissions, or service unavailability
     */
    CSCAuthorizeResponse authorize(CSCAuthorizeRequest request) throws CscAuthorizationException;
}
