package com.wpanther.xmlsigning.domain.exception;

/**
 * Thrown when CSC (Remote Signing Service) authorization fails.
 * <p>
 * This exception indicates that the {@code /csc/v2/credentials/authorize}
 * endpoint returned an error response. Possible causes include:
 * <ul>
 *   <li>Invalid credential ID</li>
 *   <li>Expired or revoked credentials</li>
 *   <li>Network connectivity issues to the CSC service</li>
 *   <li>CSC service unavailable or rate limiting</li>
 * </ul>
 * <p>
 * Retry behavior: Authorization failures are typically transient and may
 * be retried with exponential backoff, up to the configured {@code max-retries}.
 */
public class CscAuthorizationException extends XmlSigningException {

    private final String credentialId;

    public CscAuthorizationException(String message, String credentialId) {
        super(message);
        this.credentialId = credentialId;
    }

    public CscAuthorizationException(String message, Throwable cause, String credentialId) {
        super(message, cause);
        this.credentialId = credentialId;
    }

    public String getCredentialId() {
        return credentialId;
    }
}
