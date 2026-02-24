package com.wpanther.xmlsigning.domain.exception;

/**
 * Thrown when CSC (Remote Signing Service) authorization fails.
 * <p>
 * This exception indicates that the {@code /csc/v2/credentials/authorize}
 * endpoint returned an error response. Possible causes include:
 * <ul>
 *   <li>Invalid client ID or credential ID</li>
 *   <li>Expired or revoked credentials</li>
 *   <li>Network connectivity issues to the CSC service</li>
 *   <li>CSC service unavailable or rate limiting</li>
 * </ul>
 * <p>
 * Retry behavior: Authorization failures are typically transient and may
 * be retried with exponential backoff, up to the configured {@code max-retries}.
 */
public class CscAuthorizationException extends XmlSigningException {

    private final String clientId;
    private final String credentialId;

    /**
     * Constructs a new CSC authorization exception.
     *
     * @param message     the detail message
     * @param clientId    the CSC client ID that was used
     * @param credentialId the CSC credential ID that was used
     */
    public CscAuthorizationException(String message, String clientId, String credentialId) {
        super(message);
        this.clientId = clientId;
        this.credentialId = credentialId;
    }

    /**
     * Constructs a new CSC authorization exception with a cause.
     *
     * @param message     the detail message
     * @param cause       the cause of the exception
     * @param clientId    the CSC client ID that was used
     * @param credentialId the CSC credential ID that was used
     */
    public CscAuthorizationException(String message, Throwable cause, String clientId, String credentialId) {
        super(message, cause);
        this.clientId = clientId;
        this.credentialId = credentialId;
    }

    /**
     * Returns the CSC client ID that was used when authorization failed.
     *
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the CSC credential ID that was used when authorization failed.
     *
     * @return the credential ID
     */
    public String getCredentialId() {
        return credentialId;
    }
}
