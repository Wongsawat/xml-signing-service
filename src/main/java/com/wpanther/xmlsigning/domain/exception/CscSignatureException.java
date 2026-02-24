package com.wpanther.xmlsigning.domain.exception;

/**
 * Thrown when CSC (Remote Signing Service) signature operation fails.
 * <p>
 * This exception indicates that the {@code /csc/v2/signatures/signHash}
 * endpoint returned an error response. Possible causes include:
 * <ul>
 *   <li>Invalid SAD token (expired or already used)</li>
 *   <li>Hash algorithm mismatch</li>
 *   <li>Signature level not supported by credential</li>
 *   <li>Network connectivity issues to the CSC service</li>
 *   <li>CSC service unavailable or rate limiting</li>
 * </ul>
 * <p>
 * Retry behavior: Signature failures with a valid SAD token should NOT
 * be retried (the SAD token is single-use). However, transient network
 * errors may be retried by obtaining a new SAD token via authorization.
 */
public class CscSignatureException extends XmlSigningException {

    private final String transactionId;

    /**
     * Constructs a new CSC signature exception.
     *
     * @param message the detail message
     */
    public CscSignatureException(String message) {
        super(message);
        this.transactionId = null;
    }

    /**
     * Constructs a new CSC signature exception with a cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public CscSignatureException(String message, Throwable cause) {
        super(message, cause);
        this.transactionId = null;
    }

    /**
     * Constructs a new CSC signature exception with a transaction ID.
     *
     * @param message      the detail message
     * @param transactionId the CSC transaction ID (if available from authorize response)
     */
    public CscSignatureException(String message, String transactionId) {
        super(message);
        this.transactionId = transactionId;
    }

    /**
     * Constructs a new CSC signature exception with a cause and transaction ID.
     *
     * @param message      the detail message
     * @param cause        the cause of the exception
     * @param transactionId the CSC transaction ID (if available from authorize response)
     */
    public CscSignatureException(String message, Throwable cause, String transactionId) {
        super(message, cause);
        this.transactionId = transactionId;
    }

    /**
     * Returns the CSC transaction ID associated with the failed signature operation.
     *
     * @return the transaction ID, or null if not available
     */
    public String getTransactionId() {
        return transactionId;
    }
}
