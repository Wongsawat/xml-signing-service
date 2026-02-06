package com.wpanther.xmlsigning.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing signed XML document identity
 */
public record SignedXmlDocumentId(UUID value) {

    public SignedXmlDocumentId {
        Objects.requireNonNull(value, "Document ID cannot be null");
    }

    public static SignedXmlDocumentId create() {
        return new SignedXmlDocumentId(UUID.randomUUID());
    }

    public static SignedXmlDocumentId from(String id) {
        try {
            return new SignedXmlDocumentId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document ID format: " + id, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
