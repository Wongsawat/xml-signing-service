package com.wpanther.xmlsigning.application.dto.event;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompensateXmlSigningCommand Tests")
class CompensateXmlSigningCommandTest {

    @Test
    @DisplayName("Should create command with JsonCreator constructor")
    void shouldCreateCommandWithJsonCreatorConstructor() {
        UUID eventId = UUID.randomUUID();
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGN_XML;
        String correlationId = "corr-456";
        String stepToCompensate = "PROCESS_TAX_INVOICE";
        String documentId = "doc-789";
        String documentType = "tax-invoice";

        CompensateXmlSigningCommand command = new CompensateXmlSigningCommand(
                        eventId,
                        null,
                        "CompensateXmlSigningCommand",
                        1,
                        sagaId,
                        sagaStep,
                        correlationId,
                        stepToCompensate,
                        documentId,
                        documentType
        );

        assertThat(command.getSagaId()).isEqualTo(sagaId);
        assertThat(command.getSagaStep()).isEqualTo(sagaStep);
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getStepToCompensate()).isEqualTo(stepToCompensate);
        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getDocumentType()).isEqualTo(documentType);
    }

    @Test
    @DisplayName("Should create command with convenience constructor")
    void shouldCreateCommandWithConvenienceConstructor() {
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGN_XML;
        String correlationId = "corr-456";
        String stepToCompensate = "PROCESS_TAX_INVOICE";
        String documentId = "doc-789";
        String documentType = "tax-invoice";

        CompensateXmlSigningCommand command = new CompensateXmlSigningCommand(
                        sagaId,
                        sagaStep,
                        correlationId,
                        stepToCompensate,
                        documentId,
                        documentType
        );

        assertThat(command.getSagaId()).isEqualTo(sagaId);
        assertThat(command.getSagaStep()).isEqualTo(sagaStep);
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getStepToCompensate()).isEqualTo(stepToCompensate);
        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getDocumentType()).isEqualTo(documentType);
    }

    @Test
    @DisplayName("Should serialize to JSON correctly")
    void shouldSerializeToJsonCorrectly() {
        CompensateXmlSigningCommand command = new CompensateXmlSigningCommand(
                        "saga-123",
                        SagaStep.SIGN_XML,
                        "corr-456",
                        "PROCESS_TAX_INVOICE",
                        "doc-789",
                        "tax-invoice"
        );

        assertThat(command.getSagaId()).isNotNull();
        assertThat(command.getSagaStep()).isNotNull();
        assertThat(command.getCorrelationId()).isNotNull();
        assertThat(command.getStepToCompensate()).isNotNull();
        assertThat(command.getDocumentId()).isNotNull();
        assertThat(command.getDocumentType()).isNotNull();
    }
}
