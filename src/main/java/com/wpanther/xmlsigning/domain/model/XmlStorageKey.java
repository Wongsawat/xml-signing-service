package com.wpanther.xmlsigning.domain.model;

/**
 * Value object representing the storage key for an XML document in object storage.
 * Wraps the raw S3/MinIO key string to make port signatures type-safe.
 */
public record XmlStorageKey(String value) {
    public XmlStorageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("XmlStorageKey value must not be blank");
        }
    }
}
