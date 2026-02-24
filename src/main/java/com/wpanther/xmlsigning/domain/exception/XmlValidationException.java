package com.wpanther.xmlsigning.domain.exception;

/**
 * Thrown when XML validation or parsing fails.
 * <p>
 * This exception indicates failures when validating or parsing XML documents.
 * Possible causes include:
 * <ul>
 *   <li>Malformed XML (syntax errors)</li>
 *   <li>XXE (XML External Entity) attack attempts blocked by security parser</li>
 *   <li>Document type detection failure (unknown namespace or root element)</li>
 *   <li>XML content too large or too small</li>
 *   <li>Invalid XPath expression</li>
 * </ul>
 * <p>
 * XML validation failures are typically permanent (not retryable) as they
 * indicate malformed input data. The caller should fix the XML content
 * before retrying.
 */
public class XmlValidationException extends XmlSigningException {

    private final String validationType;
    private final Long contentLength;

    /**
     * Constructs a new XML validation exception.
     *
     * @param message        the detail message
     * @param validationType the type of validation that failed
     *                       (e.g., "parse", "detect-type", "size-check")
     */
    public XmlValidationException(String message, String validationType) {
        super(message);
        this.validationType = validationType;
        this.contentLength = null;
    }

    /**
     * Constructs a new XML validation exception with a cause.
     *
     * @param message        the detail message
     * @param cause          the cause of the exception
     * @param validationType the type of validation that failed
     */
    public XmlValidationException(String message, Throwable cause, String validationType) {
        super(message, cause);
        this.validationType = validationType;
        this.contentLength = null;
    }

    /**
     * Constructs a new XML validation exception with content length.
     *
     * @param message        the detail message
     * @param validationType the type of validation that failed
     * @param contentLength  the length of the XML content (may be null)
     */
    public XmlValidationException(String message, String validationType, Long contentLength) {
        super(message);
        this.validationType = validationType;
        this.contentLength = contentLength;
    }

    /**
     * Returns the type of validation that failed.
     *
     * @return the validation type (e.g., "parse", "detect-type", "size-check")
     */
    public String getValidationType() {
        return validationType;
    }

    /**
     * Returns the content length of the XML that failed validation.
     *
     * @return the content length in characters, or null if not available
     */
    public Long getContentLength() {
        return contentLength;
    }
}
