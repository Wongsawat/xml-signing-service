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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.lang.reflect.Field;

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
    }

    private void setField(String name, String value) throws Exception {
        Field f = MinioStorageService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    @DisplayName("upload() calls s3Client.putObject with correct bucket and content type")
    void testUploadCallsPutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.upload("inv-001", "INVOICE", "<signed>xml</signed>");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo(BUCKET);
        assertThat(req.contentType()).isEqualTo("application/xml");
        assertThat(req.key()).isNotBlank();
    }

    @Test
    @DisplayName("upload() returns an S3 key containing the document type")
    void testUploadReturnsKeyWithDocumentType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key = service.upload("inv-001", "TAX_INVOICE", "<signed/>");

        assertThat(key).contains("TAX_INVOICE");
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
}
