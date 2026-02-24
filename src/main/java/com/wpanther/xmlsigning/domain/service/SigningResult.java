package com.wpanther.xmlsigning.domain.service;

/**
 * Result of an XML signing operation containing the signed document
 * along with metadata from the CSC API response.
 *
 * @param signedXml       The XAdES-BASELINE-T signed XML document
 * @param certificate      The X.509 certificate chain from CSC (base64-encoded)
 * @param transactionId    The transaction ID from CSC authorization
 */
public record SigningResult(
        String signedXml,
        String certificate,
        String transactionId
) {
    public SigningResult {
        requireNonBlank(signedXml, "signedXml");
        // certificate may be null
        requireNonBlank(transactionId, "transactionId");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
