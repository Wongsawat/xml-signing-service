package com.wpanther.xmlsigning.domain.event;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessXmlSigningCommand Tests")
class ProcessXmlSigningCommandTest {

    @Test
    @DisplayName("Should create command with JsonCreator constructor")
    void shouldCreateCommandWithJsonCreatorConstructor() {
        UUID eventId = UUID.randomUUID();
        String sagaId = "saga-123";
        String sagaStep = "SIGN_XML";
        String correlationId = "corr-456";
        String documentId = "doc-789";
        String xmlContent = "<xml>content</xml>";
        String invoiceNumber = "INV-001";
        String documentType = DocumentType.TAX_INVOICE.name();

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
                        eventId,
                        null,
                        "ProcessXmlSigningCommand",
                        1,
                        sagaId,
                        sagaStep,
                        correlationId,
                        documentId,
                        xmlContent,
                        invoiceNumber,
                        documentType
        );

        assertThat(command.getSagaId()).isEqualTo(sagaId);
        assertThat(command.getSagaStep()).isEqualTo(sagaStep);
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getXmlContent()).isEqualTo(xmlContent);
        assertThat(command.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(command.getDocumentType()).isEqualTo(documentType);
    }

    @Test
    @DisplayName("Should create command with convenience constructor")
    void shouldCreateCommandWithConvenienceConstructor() {
        String sagaId = "saga-123";
        String sagaStep = "SIGN_XML";
        String correlationId = "corr-456";
        String documentId = "doc-789";
        String xmlContent = "<xml>content</xml>";
        String invoiceNumber = "INV-001";
        String documentType = DocumentType.TAX_INVOICE.name();

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
                        sagaId,
                        sagaStep,
                        correlationId,
                        documentId,
                        xmlContent,
                        invoiceNumber,
                        documentType
        );

        assertThat(command.getSagaId()).isEqualTo(sagaId);
        assertThat(command.getSagaStep()).isEqualTo(sagaStep);
        assertThat(command.getCorrelationId()).isEqualTo(correlationId);
        assertThat(command.getDocumentId()).isEqualTo(documentId);
        assertThat(command.getXmlContent()).isEqualTo(xmlContent);
        assertThat(command.getInvoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(command.getDocumentType()).isEqualTo(documentType);
    }

    @Test
    @DisplayName("Should serialize to JSON correctly")
    void shouldSerializeToJsonCorrectly() {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
                        "saga-123",
                        "SIGN_XML",
                        "corr-456",
                        "doc-789",
                        "<xml>content</xml>",
                        "INV-001",
                        DocumentType.TAX_INVOICE.name()
        );

        assertThat(command.getSagaId()).isNotNull();
        assertThat(command.getSagaStep()).isNotNull();
        assertThat(command.getCorrelationId()).isNotNull();
        assertThat(command.getDocumentId()).isNotNull();
        assertThat(command.getXmlContent()).isNotNull();
        assertThat(command.getInvoiceNumber()).isNotNull();
        assertThat(command.getDocumentType()).isNotNull();
    }
}
