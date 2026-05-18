package com.wpanther.xmlsigning.application.usecase;

public record SigningResult(
        String signedXml,
        String certificate,
        String responseId
) {
    public SigningResult {
        requireNonBlank(signedXml, "signedXml");
        requireNonBlank(responseId, "responseId");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}