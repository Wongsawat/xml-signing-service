package com.wpanther.xmlsigning.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Exceptions")
class DomainExceptionTest {

    @Nested
    @DisplayName("XmlSigningException")
    class XmlSigningExceptionTests {

        @Test
        @DisplayName("constructs with message only")
        void messageOnly() {
            var ex = new XmlSigningException("test message");
            assertThat(ex.getMessage()).isEqualTo("test message");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("constructs with message and cause")
        void messageAndCause() {
            var cause = new RuntimeException("root cause");
            var ex = new XmlSigningException("test message", cause);
            assertThat(ex.getMessage()).isEqualTo("test message");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("CscAuthorizationException")
    class CscAuthorizationExceptionTests {

        @Test
        @DisplayName("constructs with message only")
        void messageOnly() {
            var ex = new CscAuthorizationException("auth failed", "client-1", "cred-1");
            assertThat(ex.getMessage()).isEqualTo("auth failed");
            assertThat(ex.getClientId()).isEqualTo("client-1");
            assertThat(ex.getCredentialId()).isEqualTo("cred-1");
        }

        @Test
        @DisplayName("constructs with message and cause")
        void messageAndCause() {
            var cause = new RuntimeException("network error");
            var ex = new CscAuthorizationException("auth failed", cause, "client-1", "cred-1");
            assertThat(ex.getMessage()).isEqualTo("auth failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getClientId()).isEqualTo("client-1");
            assertThat(ex.getCredentialId()).isEqualTo("cred-1");
        }
    }

    @Nested
    @DisplayName("CscSignatureException")
    class CscSignatureExceptionTests {

        @Test
        @DisplayName("constructs with message only")
        void messageOnly() {
            var ex = new CscSignatureException("signature failed");
            assertThat(ex.getMessage()).isEqualTo("signature failed");
            assertThat(ex.getTransactionId()).isNull();
        }

        @Test
        @DisplayName("constructs with message and cause")
        void messageAndCause() {
            var cause = new RuntimeException("network error");
            var ex = new CscSignatureException("signature failed", cause);
            assertThat(ex.getMessage()).isEqualTo("signature failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getTransactionId()).isNull();
        }

        @Test
        @DisplayName("constructs with message and transactionId")
        void messageAndTransactionId() {
            var ex = new CscSignatureException("signature failed", "txn-123");
            assertThat(ex.getMessage()).isEqualTo("signature failed");
            assertThat(ex.getTransactionId()).isEqualTo("txn-123");
        }

        @Test
        @DisplayName("constructs with message, cause and transactionId")
        void messageCauseAndTransactionId() {
            var cause = new RuntimeException("network error");
            var ex = new CscSignatureException("signature failed", cause, "txn-456");
            assertThat(ex.getMessage()).isEqualTo("signature failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getTransactionId()).isEqualTo("txn-456");
        }
    }

    @Nested
    @DisplayName("DocumentStorageException")
    class DocumentStorageExceptionTests {

        @Test
        @DisplayName("constructs with message, operation and s3Key")
        void basicConstructor() {
            var ex = new DocumentStorageException("upload failed", "upload", "path/to/file.xml");
            assertThat(ex.getMessage()).isEqualTo("upload failed");
            assertThat(ex.getOperation()).isEqualTo("upload");
            assertThat(ex.getS3Key()).isEqualTo("path/to/file.xml");
        }

        @Test
        @DisplayName("constructs with message, cause, operation and s3Key")
        void withCause() {
            var cause = new RuntimeException("S3 unavailable");
            var ex = new DocumentStorageException("upload failed", cause, "upload", "path/to/file.xml");
            assertThat(ex.getMessage()).isEqualTo("upload failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getOperation()).isEqualTo("upload");
            assertThat(ex.getS3Key()).isEqualTo("path/to/file.xml");
        }
    }

    @Nested
    @DisplayName("XmlValidationException")
    class XmlValidationExceptionTests {

        @Test
        @DisplayName("constructs with message and validationType")
        void basicConstructor() {
            var ex = new XmlValidationException("parse failed", "parse");
            assertThat(ex.getMessage()).isEqualTo("parse failed");
            assertThat(ex.getValidationType()).isEqualTo("parse");
            assertThat(ex.getContentLength()).isNull();
        }

        @Test
        @DisplayName("constructs with message, cause and validationType")
        void withCause() {
            var cause = new RuntimeException("invalid XML");
            var ex = new XmlValidationException("parse failed", cause, "parse");
            assertThat(ex.getMessage()).isEqualTo("parse failed");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getValidationType()).isEqualTo("parse");
            assertThat(ex.getContentLength()).isNull();
        }

        @Test
        @DisplayName("constructs with message, validationType and contentLength")
        void withContentLength() {
            var ex = new XmlValidationException("size check failed", "size-check", 0L);
            assertThat(ex.getMessage()).isEqualTo("size check failed");
            assertThat(ex.getValidationType()).isEqualTo("size-check");
            assertThat(ex.getContentLength()).isEqualTo(0L);
        }
    }
}
