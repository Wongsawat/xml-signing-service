package com.wpanther.xmlsigning.application.port.out;

import com.wpanther.xmlsigning.domain.exception.DocumentStorageException;
import com.wpanther.xmlsigning.domain.model.StorageResult;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;

/**
 * Outbound port for storing and managing XML documents in object storage.
 * Implementations provide storage via MinIO, S3, or other backends.
 */
public interface XmlStoragePort {

    /**
     * Store the original (unsigned) XML document.
     *
     * @return storage key identifying the stored object
     * @throws DocumentStorageException if storage fails
     */
    XmlStorageKey storeOriginalXml(String invoiceId, String documentType, String xmlContent);

    /**
     * Store a signed XML document.
     *
     * @return storage result containing key and size of the stored object
     * @throws DocumentStorageException if storage fails
     */
    StorageResult storeSignedXml(String invoiceId, String documentType, String xmlContent);

    /**
     * Build the full access URL for a stored document.
     */
    String buildUrl(XmlStorageKey key);

    /**
     * Delete a stored XML document. Used during saga compensation.
     *
     * @throws DocumentStorageException if deletion fails
     */
    void delete(XmlStorageKey key);
}
