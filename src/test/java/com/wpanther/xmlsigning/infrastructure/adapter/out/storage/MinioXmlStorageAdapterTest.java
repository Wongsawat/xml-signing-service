package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.exception.DocumentStorageException;
import com.wpanther.xmlsigning.domain.model.StorageResult;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioXmlStorageAdapterTest {

    @Mock
    private MinioStorageService storageService;

    @InjectMocks
    private MinioXmlStorageAdapter adapter;

    @Test
    void storeOriginalXml_delegatesAndWrapsKey() {
        // Given
        String documentId = "INV-001";
        String documentType = "TAX_INVOICE";
        String xmlContent = "<xml>content</xml>";
        String expectedS3Key = "2025/03/05/TAX_INVOICE/original-xml-INV-001-12345.xml";
        when(storageService.uploadOriginalXml(documentId, documentType, xmlContent))
                .thenReturn(expectedS3Key);

        // When
        XmlStorageKey result = adapter.storeOriginalXml(documentId, documentType, xmlContent);

        // Then
        assertNotNull(result);
        assertEquals(expectedS3Key, result.value());
        verify(storageService).uploadOriginalXml(documentId, documentType, xmlContent);
    }

    @Test
    void storeSignedXml_delegatesAndWrapsKey() {
        // Given
        String documentId = "INV-002";
        String documentType = "INVOICE";
        String xmlContent = "<signed>xml</signed>";
        String expectedS3Key = "2025/03/05/INVOICE/signed-xml-INV-002-67890.xml";
        StorageResult storageResult = new StorageResult(new XmlStorageKey(expectedS3Key), 1024L);
        when(storageService.upload(documentId, documentType, xmlContent))
                .thenReturn(storageResult);

        // When
        StorageResult result = adapter.storeSignedXml(documentId, documentType, xmlContent);

        // Then
        assertNotNull(result);
        assertEquals(expectedS3Key, result.key().value());
        assertEquals(1024L, result.sizeBytes());
        verify(storageService).upload(documentId, documentType, xmlContent);
    }

    @Test
    void buildUrl_unwrapsKeyAndDelegates() {
        // Given
        XmlStorageKey key = new XmlStorageKey("test/s3/key.xml");
        String expectedUrl = "http://minio:9000/bucket/test/s3/key.xml";
        when(storageService.buildUrl("test/s3/key.xml")).thenReturn(expectedUrl);

        // When
        String result = adapter.buildUrl(key);

        // Then
        assertEquals(expectedUrl, result);
        verify(storageService).buildUrl("test/s3/key.xml");
    }

    @Test
    void delete_unwrapsKeyAndDelegates() {
        // Given
        XmlStorageKey key = new XmlStorageKey("test/s3/key.xml");
        doNothing().when(storageService).delete("test/s3/key.xml");

        // When
        adapter.delete(key);

        // Then
        verify(storageService).delete("test/s3/key.xml");
    }

    @Test
    void storeOriginalXml_propagatesDocumentStorageException() {
        // Given
        String documentId = "INV-003";
        String documentType = "RECEIPT";
        String xmlContent = "<xml>error</xml>";
        DocumentStorageException expectedException = new DocumentStorageException(
                "Upload failed",
                "upload-original",
                "unknown"
        );
        when(storageService.uploadOriginalXml(documentId, documentType, xmlContent))
                .thenThrow(expectedException);

        // When/Then
        DocumentStorageException thrown = assertThrows(
                DocumentStorageException.class,
                () -> adapter.storeOriginalXml(documentId, documentType, xmlContent)
        );
        assertSame(expectedException, thrown);
        verify(storageService).uploadOriginalXml(documentId, documentType, xmlContent);
    }
}
