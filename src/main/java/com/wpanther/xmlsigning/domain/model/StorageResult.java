package com.wpanther.xmlsigning.domain.model;

/**
 * Value object representing the result of an XML document storage operation.
 * Contains both the storage key and the size of the stored content in bytes.
 *
 * @param key the storage key (S3/MinIO key)
 * @param sizeBytes the size of the stored content in bytes
 */
public record StorageResult(XmlStorageKey key, long sizeBytes) {

    public StorageResult {
        if (key == null) {
            throw new IllegalArgumentException("StorageResult key must not be null");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("StorageResult sizeBytes must be positive, got: " + sizeBytes);
        }
    }
}
