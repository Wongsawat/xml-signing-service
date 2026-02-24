package com.wpanther.xmlsigning.domain.exception;

/**
 * Thrown when document storage operations fail.
 * <p>
 * This exception indicates failures when interacting with the storage backend
 * (MinIO/S3-compatible object storage). Possible causes include:
 * <ul>
 *   <li>Storage service unavailable or network connectivity issues</li>
 *   <li>Bucket does not exist or insufficient permissions</li>
 *   <li>Quota exceeded or storage full</li>
 *   <li>Invalid S3 key or path</li>
 * </ul>
 * <p>
 * Storage failures are typically transient and may be retried. However,
 * after successful storage, compensation (delete) should also be attempted
 * to maintain data consistency.
 */
public class DocumentStorageException extends XmlSigningException {

    private final String operation;
    private final String s3Key;

    /**
     * Constructs a new document storage exception.
     *
     * @param message   the detail message
     * @param operation the operation that failed (e.g., "upload", "delete", "buildUrl")
     * @param s3Key     the S3 key being operated on (may be null)
     */
    public DocumentStorageException(String message, String operation, String s3Key) {
        super(message);
        this.operation = operation;
        this.s3Key = s3Key;
    }

    /**
     * Constructs a new document storage exception with a cause.
     *
     * @param message   the detail message
     * @param cause     the cause of the exception
     * @param operation the operation that failed (e.g., "upload", "delete", "buildUrl")
     * @param s3Key     the S3 key being operated on (may be null)
     */
    public DocumentStorageException(String message, Throwable cause, String operation, String s3Key) {
        super(message, cause);
        this.operation = operation;
        this.s3Key = s3Key;
    }

    /**
     * Returns the operation that failed.
     *
     * @return the operation name (e.g., "upload", "delete", "buildUrl")
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the S3 key that was being operated on.
     *
     * @return the S3 key, or null if not applicable
     */
    public String getS3Key() {
        return s3Key;
    }
}
