package com.wpanther.xmlsigning.domain.exception;

/**
 * Base exception for all XML signing domain failures.
 * <p>
 * Application code should catch specific subclasses when handling expected
 * failure scenarios (e.g., {@link CscAuthorizationException},
 * {@link DocumentStorageException}). Catch this base class only for
 * generic error handling or logging.
 */
public class XmlSigningException extends RuntimeException {

    /**
     * Constructs a new XML signing exception with the specified message.
     *
     * @param message the detail message
     */
    public XmlSigningException(String message) {
        super(message);
    }

    /**
     * Constructs a new XML signing exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public XmlSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
