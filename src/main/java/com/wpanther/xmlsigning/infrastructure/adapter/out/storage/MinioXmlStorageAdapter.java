package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.domain.port.out.XmlStoragePort;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for MinIO XML document storage.
 * Wraps {@link MinioStorageService} and implements the {@link XmlStoragePort} interface
 * using typed value objects ({@link XmlStorageKey}) instead of raw strings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioXmlStorageAdapter implements XmlStoragePort {

    private final MinioStorageService storageService;

    @Override
    public XmlStorageKey storeOriginalXml(String invoiceId, String documentType, String xmlContent) {
        String s3Key = storageService.uploadOriginalXml(invoiceId, documentType, xmlContent);
        return new XmlStorageKey(s3Key);
    }

    @Override
    public XmlStorageKey storeSignedXml(String invoiceId, String documentType, String xmlContent) {
        String s3Key = storageService.upload(invoiceId, documentType, xmlContent);
        return new XmlStorageKey(s3Key);
    }

    @Override
    public String buildUrl(XmlStorageKey key) {
        return storageService.buildUrl(key.value());
    }

    @Override
    public void delete(XmlStorageKey key) {
        storageService.delete(key.value());
    }
}
