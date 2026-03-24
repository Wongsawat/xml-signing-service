package com.wpanther.xmlsigning.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.lang.reflect.Field;

import com.wpanther.xmlsigning.domain.model.StorageResult;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioStorageService")
class MinioStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private MinioStorageService service;

    private static final String BUCKET = "signed-xml-documents";
    private static final String BASE_URL = "http://localhost:9000/signed-xml-documents";

    @BeforeEach
    void setUp() throws Exception {
        service = new MinioStorageService(s3Client);
        setField("bucketName", BUCKET);
        setField("baseUrl", BASE_URL);
        setField("maxUploadSizeBytes", 102400);
    }

    private void setField(String name, String value) throws Exception {
        Field f = MinioStorageService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private void setField(String name, int value) throws Exception {
        Field f = MinioStorageService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    @DisplayName("upload() calls s3Client.putObject with correct bucket and content type")
    void testUploadCallsPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageResult result = service.upload("inv-001", "INVOICE", "<signed>xml</signed>");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo(BUCKET);
        assertThat(req.contentType()).isEqualTo("application/xml");
        assertThat(req.key()).isNotBlank();
        assertThat(result.sizeBytes()).isEqualTo((long) "<signed>xml</signed>".getBytes().length);
    }

    @Test
    @DisplayName("upload() returns an S3 key containing the document type")
    void testUploadReturnsKeyWithDocumentType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageResult result = service.upload("inv-001", "TAX_INVOICE", "<signed/>");

        assertThat(result.key().value()).contains("TAX_INVOICE");
    }

    @Test
    @DisplayName("upload() sanitizes special characters in invoiceId for the filename")
    void testUploadSanitizesInvoiceId() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        service.upload("inv/001?foo", "INVOICE", "<signed/>");
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        // Key must not contain '/' or '?' from the invoiceId part (only date/type separators)
        String key = captor.getValue().key();
        // The filename part (last segment) should not contain / or ?
        String filename = key.substring(key.lastIndexOf('/') + 1);
        assertThat(filename).doesNotContain("?");
    }

    @Test
    @DisplayName("upload() sets correct content length")
    void testUploadSetsContentLength() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String content = "<signed>xml content</signed>";
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        service.upload("inv-001", "INVOICE", content);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        assertThat(captor.getValue().contentLength()).isEqualTo((long) content.getBytes().length);
    }

    @Test
    @DisplayName("buildUrl() concatenates base URL and S3 key")
    void testBuildUrl() {
        String key = "2024/01/15/INVOICE/signed-xml-inv-001-uuid.xml";
        String url = service.buildUrl(key);

        assertThat(url).isEqualTo(BASE_URL + "/" + key);
    }

    @Test
    @DisplayName("delete() calls s3Client.deleteObject with correct bucket and key")
    void testDeleteCallsDeleteObject() {
        String key = "2024/01/15/INVOICE/signed-xml-inv-001-uuid.xml";
        service.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("delete() wraps S3 exception in DocumentStorageException")
    void testDeleteWrapsException() {
        String key = "2024/01/15/INVOICE/signed-xml-inv-001-uuid.xml";
        doThrow(new RuntimeException("S3 error")).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> service.delete(key))
                .isExactlyInstanceOf(com.wpanther.xmlsigning.domain.exception.DocumentStorageException.class)
                .hasMessageContaining("Failed to delete from MinIO");
    }

    @Test
    @DisplayName("uploadOriginalXml() calls s3Client.putObject with correct bucket and content type")
    void testUploadOriginalXmlCallsPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.uploadOriginalXml("inv-001", "INVOICE", "<original>xml</original>");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo(BUCKET);
        assertThat(req.contentType()).isEqualTo("application/xml");
        assertThat(req.key()).isNotBlank();
    }

    @Test
    @DisplayName("uploadOriginalXml() returns an S3 key containing the document type")
    void testUploadOriginalXmlReturnsKeyWithDocumentType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.uploadOriginalXml("inv-001", "TAX_INVOICE", "<original/>");

        assertThat(key).contains("TAX_INVOICE");
        assertThat(key).contains("original-xml-");
    }

    @Test
    @DisplayName("uploadOriginalXml() wraps S3 exception in DocumentStorageException")
    void testUploadOriginalXmlWrapsException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.uploadOriginalXml("inv-001", "INVOICE", "<original/>"))
                .isExactlyInstanceOf(com.wpanther.xmlsigning.domain.exception.DocumentStorageException.class)
                .hasMessageContaining("Failed to upload original XML to MinIO");
    }

    @Test
    @DisplayName("upload() wraps DocumentStorageException as-is")
    void testUploadWrapsDocumentStorageExceptionAsIs() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.upload("inv-001", "INVOICE", "<signed/>"))
                .isExactlyInstanceOf(com.wpanther.xmlsigning.domain.exception.DocumentStorageException.class)
                .hasMessageContaining("Failed to upload signed XML to MinIO");
    }

    @Test
    @DisplayName("listAllObjectKeys() returns all object keys from bucket")
    void testListAllObjectKeys() {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(java.util.Arrays.asList(
                        S3Object.builder().key("2024/01/15/INVOICE/signed-xml-1.xml").build(),
                        S3Object.builder().key("2024/01/15/INVOICE/signed-xml-2.xml").build()
                ))
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        var keys = service.listAllObjectKeys();

        assertThat(keys).containsExactly(
                "2024/01/15/INVOICE/signed-xml-1.xml",
                "2024/01/15/INVOICE/signed-xml-2.xml"
        );
    }

    @Test
    @DisplayName("listAllObjectKeys() handles pagination with continuation token")
    void testListAllObjectKeysWithPagination() {
        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(java.util.Arrays.asList(S3Object.builder().key("key-1.xml").build()))
                .nextContinuationToken("token-abc")
                .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(java.util.Arrays.asList(S3Object.builder().key("key-2.xml").build()))
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        var keys = service.listAllObjectKeys();

        assertThat(keys).containsExactly("key-1.xml", "key-2.xml");
    }

    @Test
    @DisplayName("listAllObjectKeys() returns empty list when bucket is empty")
    void testListAllObjectKeysEmpty() {
        ListObjectsV2Response response = ListObjectsV2Response.builder().contents(java.util.Collections.emptyList()).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        var keys = service.listAllObjectKeys();

        assertThat(keys).isEmpty();
    }

    @Test
    @DisplayName("listAllObjectKeys() wraps S3 exception in DocumentStorageException")
    void testListAllObjectKeysWrapsException() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(new RuntimeException("S3 error"));

        assertThatThrownBy(() -> service.listAllObjectKeys())
                .isExactlyInstanceOf(com.wpanther.xmlsigning.domain.exception.DocumentStorageException.class)
                .hasMessageContaining("Failed to list objects from MinIO");
    }
}
