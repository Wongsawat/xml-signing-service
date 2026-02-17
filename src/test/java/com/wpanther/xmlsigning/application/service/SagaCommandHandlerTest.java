package com.wpanther.xmlsigning.application.service;

import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.messaging.EventPublisher;
import com.wpanther.xmlsigning.infrastructure.messaging.SagaReplyPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SagaCommandHandlerTest {

    @Mock
    private SignedXmlDocumentRepository documentRepository;

    @Mock
    private XmlSigningService signingService;

    @Mock
    private DocumentTypeDetectionService documentTypeDetectionService;

    @Mock
    private SagaReplyPublisher sagaReplyPublisher;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SagaCommandHandler handler;

    /**
     * Set maxRetries field using reflection since @Value doesn't work in unit tests.
     */
    private void setMaxRetries(int value) throws Exception {
        Field maxRetriesField = SagaCommandHandler.class.getDeclaredField("maxRetries");
        maxRetriesField.setAccessible(true);
        maxRetriesField.set(handler, value);
    }

    @Test
    void testHandleProcessCommandSuccess() throws Exception {
        setMaxRetries(3);
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", "sign-xml", "corr-1",
            "doc-success", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-success")).thenReturn(Optional.empty());
        when(signingService.signXml(any(), any())).thenReturn("<signed>xml</signed>");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishSuccess("saga-1", "sign-xml", "corr-1");
        verify(sagaReplyPublisher, never()).publishFailure(any(), any(), any(), any());
        verify(eventPublisher).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandDocumentTypeDetectionFailure() throws Exception {
        setMaxRetries(3);

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", "sign-xml", "corr-1",
            "doc-type-fail", "<xml>unknown</xml>", "INV-001", null
        );

        when(documentRepository.findByInvoiceId("doc-type-fail")).thenReturn(Optional.empty());
        when(documentTypeDetectionService.detectFromXmlContent(any())).thenReturn(null);

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishFailure("saga-1", "sign-xml", "corr-1", "Document type detection failed");
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandAlreadySigned() throws Exception {
        setMaxRetries(3);

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", "sign-xml", "corr-1",
            "doc-already-signed", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        SignedXmlDocument completedDocument = SignedXmlDocument.builder()
            .invoiceId("doc-already-signed")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .signedXml("<signed>xml</signed>")
            .transactionId("TXN-123")
            .signatureLevel("XAdES-BASELINE-T")
            .status(SigningStatus.COMPLETED)
            .build();

        when(documentRepository.findByInvoiceId("doc-already-signed")).thenReturn(Optional.of(completedDocument));

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishSuccess("saga-1", "sign-xml", "corr-1");
        verify(signingService, never()).signXml(any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandMaxRetriesExceeded() throws Exception {
        setMaxRetries(3);

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", "sign-xml", "corr-1",
            "doc-max-retries", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        SignedXmlDocument failedDocument = SignedXmlDocument.builder()
            .invoiceId("doc-max-retries")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .status(SigningStatus.FAILED)
            .retryCount(3)
            .errorMessage("Previous error")
            .build();

        when(documentRepository.findByInvoiceId("doc-max-retries")).thenReturn(Optional.of(failedDocument));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishFailure("saga-1", "sign-xml", "corr-1", "Maximum retry attempts exceeded");
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandSigningFailure() throws Exception {
        setMaxRetries(3);

        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", "sign-xml", "corr-1",
            "doc-sign-fail", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-sign-fail")).thenReturn(Optional.empty());
        when(signingService.signXml(any(), any())).thenThrow(new RuntimeException("CSC API error"));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishFailure(eq("saga-1"), eq("sign-xml"), eq("corr-1"), contains("CSC API error"));
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleCompensationFound() {
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-found")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", "COMPENSATE_sign-xml", "corr-1",
            "sign-xml", "doc-comp-found", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-found")).thenReturn(Optional.of(document));

        handler.handleCompensation(compensateCommand);

        verify(documentRepository).deleteById(document.getId());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_sign-xml", "corr-1");
        verify(sagaReplyPublisher, never()).publishFailure(any(), any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleCompensationNotFound() {
        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", "COMPENSATE_sign-xml", "corr-1",
            "sign-xml", "doc-comp-not-found", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-not-found")).thenReturn(Optional.empty());

        handler.handleCompensation(compensateCommand);

        verify(documentRepository, never()).deleteById(any());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_sign-xml", "corr-1");
    }

    @Test
    void testHandleCompensationDeleteError() {
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-del-error")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", "COMPENSATE_sign-xml", "corr-1",
            "sign-xml", "doc-comp-del-error", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-del-error")).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Delete failed")).when(documentRepository).deleteById(any());

        handler.handleCompensation(compensateCommand);

        verify(sagaReplyPublisher).publishFailure(
            eq("saga-1"), eq("COMPENSATE_sign-xml"), eq("corr-1"),
            contains("Compensation failed")
        );
        verify(sagaReplyPublisher, never()).publishCompensated(any(), any(), any());
    }
}
