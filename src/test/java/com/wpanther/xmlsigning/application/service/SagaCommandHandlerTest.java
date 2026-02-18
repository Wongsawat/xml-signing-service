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
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
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

    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private SagaCommandHandler handler;

    private static final String FAKE_S3_KEY = "2024/01/15/INVOICE/signed-xml-doc-uuid.xml";
    private static final String FAKE_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_S3_KEY;

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
        when(minioStorageService.upload(any(), any(), any())).thenReturn(FAKE_S3_KEY);
        when(minioStorageService.buildUrl(any())).thenReturn(FAKE_URL);

        handler.handleProcessCommand(command);

        verify(minioStorageService).upload(eq("doc-success"), eq("INVOICE"), eq("<signed>xml</signed>"));
        verify(sagaReplyPublisher).publishSuccess(eq("saga-1"), eq("sign-xml"), eq("corr-1"),
                eq(FAKE_URL), anyLong());
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
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
        verify(minioStorageService, never()).upload(any(), any(), any());
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
            .signedXmlPath(FAKE_S3_KEY)
            .signedXmlUrl(FAKE_URL)
            .signedXmlSize(100L)
            .transactionId("TXN-123")
            .signatureLevel("XAdES-BASELINE-T")
            .status(SigningStatus.COMPLETED)
            .build();

        when(documentRepository.findByInvoiceId("doc-already-signed")).thenReturn(Optional.of(completedDocument));

        handler.handleProcessCommand(command);

        verify(sagaReplyPublisher).publishSuccess(eq("saga-1"), eq("sign-xml"), eq("corr-1"),
                eq(FAKE_URL), eq(100L));
        verify(signingService, never()).signXml(any(), any());
        verify(minioStorageService, never()).upload(any(), any(), any());
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
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
        verify(minioStorageService, never()).upload(any(), any(), any());
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
        verify(sagaReplyPublisher, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishXmlSigned(any());
        // upload never reached when signXml throws
        verify(minioStorageService, never()).upload(any(), any(), any());
    }

    @Test
    void testHandleCompensationFoundWithSignedXml() {
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-found")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .signedXmlPath(FAKE_S3_KEY)
            .signedXmlUrl(FAKE_URL)
            .signedXmlSize(200L)
            .status(SigningStatus.COMPLETED)
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", "COMPENSATE_sign-xml", "corr-1",
            "sign-xml", "doc-comp-found", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-found")).thenReturn(Optional.of(document));

        handler.handleCompensation(compensateCommand);

        verify(minioStorageService).delete(FAKE_S3_KEY);
        verify(documentRepository).deleteById(document.getId());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_sign-xml", "corr-1");
        verify(sagaReplyPublisher, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void testHandleCompensationFoundWithoutSignedXml() {
        // Document was created but signing never completed (no MinIO object)
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-pending")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXml("<xml>test</xml>")
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", "COMPENSATE_sign-xml", "corr-1",
            "sign-xml", "doc-comp-pending", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-pending")).thenReturn(Optional.of(document));

        handler.handleCompensation(compensateCommand);

        verify(minioStorageService, never()).delete(any());
        verify(documentRepository).deleteById(document.getId());
        verify(sagaReplyPublisher).publishCompensated("saga-1", "COMPENSATE_sign-xml", "corr-1");
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
        verify(minioStorageService, never()).delete(any());
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
