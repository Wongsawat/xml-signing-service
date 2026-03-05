package com.wpanther.xmlsigning.application.service;

import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.domain.port.out.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.SigningResult;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.domain.port.out.XmlSignedEventPort;
import com.wpanther.xmlsigning.domain.port.out.SagaReplyPort;
import com.wpanther.xmlsigning.domain.port.out.XmlStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
    private SagaReplyPort sagaReplyPort;

    @Mock
    private XmlSignedEventPort xmlSignedEventPort;

    @Mock
    private XmlStoragePort xmlStoragePort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private SagaCommandHandler handler;

    private static final String FAKE_ORIGINAL_S3_KEY = "2024/01/15/INVOICE/original-xml-doc-uuid.xml";
    private static final String FAKE_ORIGINAL_URL    = "http://localhost:9000/signed-xml-documents/" + FAKE_ORIGINAL_S3_KEY;
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

    @BeforeEach
    void setUpMocks() throws Exception {
        setMaxRetries(3);
        // Make TransactionTemplate transparent: just invoke the callback directly.
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void testHandleProcessCommandSuccess() {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "doc-success", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-success")).thenReturn(Optional.empty());
        when(signingService.signXml(any(), any())).thenReturn(
                new SigningResult("<signed>xml</signed>", "FAKE-CERT", "CSC-TXN-123"));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xmlStoragePort.storeOriginalXml(any(), any(), any())).thenReturn(new XmlStorageKey(FAKE_ORIGINAL_S3_KEY));
        when(xmlStoragePort.storeSignedXml(any(), any(), any())).thenReturn(new XmlStorageKey(FAKE_S3_KEY));
        when(xmlStoragePort.buildUrl(any())).thenReturn(FAKE_URL);

        handler.handleProcessCommand(command);

        verify(xmlStoragePort).storeOriginalXml(eq("doc-success"), eq("INVOICE"), eq("<xml>test</xml>"));
        verify(xmlStoragePort).storeSignedXml(eq("doc-success"), eq("INVOICE"), eq("<signed>xml</signed>"));
        verify(sagaReplyPort).publishSuccess(eq("saga-1"), eq(SagaStep.SIGN_XML), eq("corr-1"),
                eq(FAKE_URL), anyLong());
        verify(sagaReplyPort, never()).publishFailure(any(), any(), any(), any());
        verify(xmlSignedEventPort).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandDocumentTypeDetectionFailure() {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "doc-type-fail", "<xml>unknown</xml>", "INV-001", null
        );

        when(documentRepository.findByInvoiceId("doc-type-fail")).thenReturn(Optional.empty());
        when(documentTypeDetectionService.detectFromXmlContent(any())).thenReturn(null);

        handler.handleProcessCommand(command);

        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.SIGN_XML, "corr-1", "Document type detection failed");
        verify(sagaReplyPort, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(xmlSignedEventPort, never()).publishXmlSigned(any());
        verify(xmlStoragePort, never()).storeSignedXml(any(), any(), any());
        verify(xmlStoragePort, never()).storeOriginalXml(any(), any(), any());
    }

    @Test
    void testHandleProcessCommandAlreadySigned() throws Exception {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "doc-already-signed", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        SignedXmlDocument completedDocument = SignedXmlDocument.builder()
            .invoiceId("doc-already-signed")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
            .originalXmlUrl(FAKE_ORIGINAL_URL)
            .signedXmlPath(FAKE_S3_KEY)
            .signedXmlUrl(FAKE_URL)
            .signedXmlSize(100L)
            .transactionId("TXN-123")
            .signatureLevel("XAdES-BASELINE-T")
            .status(SigningStatus.COMPLETED)
            .build();

        when(documentRepository.findByInvoiceId("doc-already-signed")).thenReturn(Optional.of(completedDocument));

        handler.handleProcessCommand(command);

        verify(sagaReplyPort).publishSuccess(eq("saga-1"), eq(SagaStep.SIGN_XML), eq("corr-1"),
                eq(FAKE_URL), eq(100L));
        verify(signingService, never()).signXml(any(), any());
        verify(xmlStoragePort, never()).storeSignedXml(any(), any(), any());
        verify(xmlStoragePort, never()).storeOriginalXml(any(), any(), any());
        verify(xmlSignedEventPort, never()).publishXmlSigned(any());
    }

    @Test
    void testHandleProcessCommandMaxRetriesExceeded() throws Exception {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "doc-max-retries", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        SignedXmlDocument failedDocument = SignedXmlDocument.builder()
            .invoiceId("doc-max-retries")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
            .status(SigningStatus.FAILED)
            .retryCount(3)
            .errorMessage("Previous error")
            .build();

        when(documentRepository.findByInvoiceId("doc-max-retries")).thenReturn(Optional.of(failedDocument));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handleProcessCommand(command);

        verify(sagaReplyPort).publishFailure("saga-1", SagaStep.SIGN_XML, "corr-1", "Maximum retry attempts exceeded");
        verify(sagaReplyPort, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(xmlSignedEventPort, never()).publishXmlSigned(any());
        verify(xmlStoragePort, never()).storeSignedXml(any(), any(), any());
        verify(xmlStoragePort, never()).storeOriginalXml(any(), any(), any());
    }

    @Test
    void testHandleProcessCommandSigningFailure() throws Exception {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "doc-sign-fail", "<xml>test</xml>", "INV-001", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-sign-fail")).thenReturn(Optional.empty());
        when(signingService.signXml(any(), any())).thenThrow(new RuntimeException("CSC API error"));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xmlStoragePort.storeOriginalXml(any(), any(), any())).thenReturn(new XmlStorageKey(FAKE_ORIGINAL_S3_KEY));
        when(xmlStoragePort.buildUrl(any())).thenReturn(FAKE_ORIGINAL_URL);

        handler.handleProcessCommand(command);

        verify(sagaReplyPort).publishFailure(eq("saga-1"), eq(SagaStep.SIGN_XML), eq("corr-1"), contains("CSC API error"));
        verify(sagaReplyPort, never()).publishSuccess(any(), any(), any(), any(), any());
        verify(xmlSignedEventPort, never()).publishXmlSigned(any());
        // storeSignedXml never reached when signXml throws
        verify(xmlStoragePort, never()).storeSignedXml(any(), any(), any());
        // original XML was uploaded before signing attempt
        verify(xmlStoragePort).storeOriginalXml(eq("doc-sign-fail"), eq("INVOICE"), eq("<xml>test</xml>"));
    }

    @Test
    void testHandleCompensationFoundWithSignedXml() {
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-found")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
            .originalXmlUrl(FAKE_ORIGINAL_URL)
            .signedXmlPath(FAKE_S3_KEY)
            .signedXmlUrl(FAKE_URL)
            .signedXmlSize(200L)
            .status(SigningStatus.COMPLETED)
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "sign-xml", "doc-comp-found", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-found")).thenReturn(Optional.of(document));

        handler.handleCompensation(compensateCommand);

        verify(xmlStoragePort).delete(new XmlStorageKey(FAKE_ORIGINAL_S3_KEY));
        verify(xmlStoragePort).delete(new XmlStorageKey(FAKE_S3_KEY));
        verify(documentRepository).deleteById(document.getId());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.SIGN_XML, "corr-1");
        verify(sagaReplyPort, never()).publishFailure(any(), any(), any(), any());
    }

    @Test
    void testHandleCompensationFoundWithoutSignedXml() {
        // Document was created and original XML uploaded, but signing never completed
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-pending")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
            .originalXmlUrl(FAKE_ORIGINAL_URL)
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "sign-xml", "doc-comp-pending", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-pending")).thenReturn(Optional.of(document));

        handler.handleCompensation(compensateCommand);

        // Original XML in MinIO is deleted
        verify(xmlStoragePort).delete(new XmlStorageKey(FAKE_ORIGINAL_S3_KEY));
        // Signed XML was never uploaded, so no second delete
        verify(xmlStoragePort, never()).delete(new XmlStorageKey(FAKE_S3_KEY));
        verify(documentRepository).deleteById(document.getId());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.SIGN_XML, "corr-1");
    }

    @Test
    void testHandleCompensationNotFound() {
        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "sign-xml", "doc-comp-not-found", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-not-found")).thenReturn(Optional.empty());

        handler.handleCompensation(compensateCommand);

        verify(documentRepository, never()).deleteById(any());
        verify(xmlStoragePort, never()).delete(any());
        verify(sagaReplyPort).publishCompensated("saga-1", SagaStep.SIGN_XML, "corr-1");
    }

    @Test
    void testHandleCompensationDeleteError() {
        SignedXmlDocument document = SignedXmlDocument.builder()
            .invoiceId("doc-comp-del-error")
            .invoiceNumber("INV-001")
            .documentType(DocumentType.INVOICE)
            .originalXmlPath(FAKE_ORIGINAL_S3_KEY)
            .build();

        CompensateXmlSigningCommand compensateCommand = new CompensateXmlSigningCommand(
            "saga-1", SagaStep.SIGN_XML, "corr-1",
            "sign-xml", "doc-comp-del-error", "INVOICE"
        );

        when(documentRepository.findByInvoiceId("doc-comp-del-error")).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Delete failed")).when(documentRepository).deleteById(any());

        handler.handleCompensation(compensateCommand);

        verify(sagaReplyPort).publishFailure(
            eq("saga-1"), eq(SagaStep.SIGN_XML), eq("corr-1"),
            contains("Compensation failed")
        );
        verify(sagaReplyPort, never()).publishCompensated(any(), any(), any());
    }
}
