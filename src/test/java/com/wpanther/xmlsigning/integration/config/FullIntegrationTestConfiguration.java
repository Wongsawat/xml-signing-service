package com.wpanther.xmlsigning.integration.config;

import com.wpanther.xmlsigning.integration.support.EidasRemoteSigningTestHelper;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Spring test configuration for full integration tests.
 *
 * <p>Provides three beans that are absent from the production application context:
 * <ol>
 *   <li><strong>cscBearerTokenInterceptor</strong> — a Feign {@link RequestInterceptor} that adds
 *       {@code Authorization: Bearer <token>} to every CSC API request. The token is obtained
 *       and cached by {@link EidasRemoteSigningTestHelper}.</li>
 *   <li><strong>fullIntegrationJdbcTemplate</strong> — a {@link JdbcTemplate} wired to the
 *       xml-signing-service database (xmlsigning_db) for test assertions.</li>
 *   <li><strong>minioVerificationS3Client</strong> — a standalone {@link S3Client} configured
 *       to talk to the test MinIO instance (localhost:9100) so tests can verify that objects
 *       are actually stored in the bucket.</li>
 * </ol>
 *
 * <p>This configuration is imported explicitly by {@link AbstractFullIntegrationTest}; it is
 * not auto-detected by component scanning.
 */
@TestConfiguration
@Import(TestKafkaProducerConfig.class)
public class FullIntegrationTestConfiguration {

    /**
     * Adds an OAuth2 Bearer token to every outgoing Feign request.
     *
     * <p>The token is obtained lazily on the first request and refreshed automatically
     * by {@link EidasRemoteSigningTestHelper#getValidToken()} when it nears expiry.
     * This is the only change needed to make the production Feign clients talk to
     * the real eidasremotesigning service (which requires JWT auth for all
     * {@code /csc/v2/**} endpoints except {@code /info} and {@code /oauth2/**}).
     */
    @Bean
    public RequestInterceptor cscBearerTokenInterceptor() {
        return requestTemplate -> {
            String token = EidasRemoteSigningTestHelper.getValidToken();
            if (token != null && !token.isBlank()) {
                requestTemplate.header("Authorization", "Bearer " + token);
            }
        };
    }

    /**
     * JdbcTemplate backed by the xml-signing-service DataSource (xmlsigning_db).
     * Used in test assertions to query signed_xml_documents and outbox_events tables.
     */
    @Bean("fullIntegrationJdbcTemplate")
    public JdbcTemplate fullIntegrationJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Standalone S3Client pointing at the test MinIO instance.
     *
     * <p>Used only for verification: tests call {@link #objectExistsInBucket} to confirm
     * that the signed XML (and original XML) were actually stored in MinIO. This client
     * is separate from the production {@code S3Client} bean to avoid any side effects.
     */
    @Bean("minioVerificationS3Client")
    public S3Client minioVerificationS3Client(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.region:us-east-1}") String region) {

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .build();
    }

    /**
     * Convenience static helper used from abstract test base and test classes to verify
     * that a key exists in MinIO without downloading the full object.
     *
     * <p>Cannot be a non-static method here because it is also called from helper methods
     * in the abstract base class that do not have a Spring bean reference.
     *
     * @param s3Client   the verification S3Client (use the {@code minioVerificationS3Client} bean)
     * @param bucketName MinIO bucket name
     * @param s3Key      object key (path within the bucket)
     * @return {@code true} if the object exists in the bucket
     */
    public static boolean objectExistsInBucket(S3Client s3Client, String bucketName, String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
