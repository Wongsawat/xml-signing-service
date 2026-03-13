package com.wpanther.xmlsigning.application.dto.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XmlSigningReplyEvent Tests")
class XmlSigningReplyEventTest {

    @Nested
    @DisplayName("success() factory method")
    class SuccessMethod {

        @Test
        @DisplayName("Should create SUCCESS reply")
        void shouldCreateSuccessReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.SIGN_XML;
            String correlationId = "corr-456";

            String signedXmlUrl = "http://localhost:9000/signed-xml-documents/2024/01/15/INVOICE/signed.xml";
            Long signedXmlSize = 1234L;

            XmlSigningReplyEvent reply = XmlSigningReplyEvent.success(sagaId, sagaStep, correlationId,
                    signedXmlUrl, signedXmlSize);

            assertThat(reply.getSagaId()).isEqualTo(sagaId);
            assertThat(reply.getSagaStep()).isEqualTo(sagaStep);
            assertThat(reply.getCorrelationId()).isEqualTo(correlationId);
            assertThat(reply.getStatus()).isEqualTo(ReplyStatus.SUCCESS);
            assertThat(reply.getErrorMessage()).isNull();
            assertThat(reply.getSignedXmlUrl()).isEqualTo(signedXmlUrl);
            assertThat(reply.getSignedXmlSize()).isEqualTo(signedXmlSize);
        }
    }

    @Nested
    @DisplayName("failure() factory method")
    class FailureMethod {

        @Test
        @DisplayName("Should create FAILURE reply")
        void shouldCreateFailureReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.SIGN_XML;
            String correlationId = "corr-456";
            String errorMessage = "Signing failed";

            XmlSigningReplyEvent reply = XmlSigningReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

            assertThat(reply.getSagaId()).isEqualTo(sagaId);
            assertThat(reply.getSagaStep()).isEqualTo(sagaStep);
            assertThat(reply.getCorrelationId()).isEqualTo(correlationId);
            assertThat(reply.getStatus()).isEqualTo(ReplyStatus.FAILURE);
            assertThat(reply.getErrorMessage()).isEqualTo(errorMessage);
        }
    }

    @Nested
    @DisplayName("compensated() factory method")
    class CompensatedMethod {

        @Test
        @DisplayName("Should create COMPENSATED reply")
        void shouldCreateCompensatedReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.SIGN_XML;
            String correlationId = "corr-456";

            XmlSigningReplyEvent reply = XmlSigningReplyEvent.compensated(sagaId, sagaStep, correlationId);

            assertThat(reply.getSagaId()).isEqualTo(sagaId);
            assertThat(reply.getSagaStep()).isEqualTo(sagaStep);
            assertThat(reply.getCorrelationId()).isEqualTo(correlationId);
            assertThat(reply.getStatus()).isEqualTo(ReplyStatus.COMPENSATED);
            assertThat(reply.getErrorMessage()).isNull();
        }
    }
}
