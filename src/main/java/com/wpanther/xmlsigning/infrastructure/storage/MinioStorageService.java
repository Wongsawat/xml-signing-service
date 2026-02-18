package com.wpanther.xmlsigning.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final S3Client s3Client;

    @Value("${app.minio.bucket-name}")
    private String bucketName;

    @Value("${app.minio.base-url}")
    private String baseUrl;

    /**
     * Upload signed XML content to MinIO.
     *
     * @return the S3 key — store this in the signed_xml_path column
     */
    public String upload(String invoiceId, String documentType, String xmlContent) {
        byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
        LocalDate now = LocalDate.now();
        String sanitizedInvoiceId = invoiceId.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String fileName = String.format("signed-xml-%s-%s.xml", sanitizedInvoiceId, UUID.randomUUID());
        String s3Key = String.format("%04d/%02d/%02d/%s/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), documentType, fileName);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/xml")
                .contentLength((long) xmlBytes.length)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(xmlBytes));
        log.info("Uploaded signed XML to MinIO: bucket={}, key={}", bucketName, s3Key);
        return s3Key;
    }

    /**
     * Build the full URL from an S3 key.
     */
    public String buildUrl(String s3Key) {
        return baseUrl + "/" + s3Key;
    }

    /**
     * Delete signed XML from MinIO. Called during saga compensation.
     */
    public void delete(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted signed XML from MinIO: bucket={}, key={}", bucketName, s3Key);
        } catch (Exception e) {
            log.error("Failed to delete signed XML from MinIO: key={}", s3Key, e);
            throw new RuntimeException("Failed to delete signed XML from MinIO", e);
        }
    }
}
