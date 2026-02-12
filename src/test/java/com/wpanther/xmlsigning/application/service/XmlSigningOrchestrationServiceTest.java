package com.wpanther.xmlsigning.application.service;

import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.event.XmlSigningRequestedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
import com.wpanther.xmlsigning.domain.service.DocumentTypeDetectionService;
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.infrastructure.messaging.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XmlSigningOrchestrationService}.
 * Tests the main orchestration logic for XML signing workflow.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningOrchestrationService")
class XmlSigningOrchestrationServiceTest {

    @Mock
    private SignedXmlDocumentRepository documentRepository;

    @Mock
    private XmlSigningService signingService;

    @Mock
    private DocumentTypeDetectionService documentTypeDetectionService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private XmlSigningOrchestrationService orchestrationService;

    // Helper to create a signing requested event
    private XmlSigningRequestedEvent createEvent(DocumentType documentType) {
        UUID eventId = UUID.randomUUID();
        return new XmlSigningRequestedEvent(
                "inv-001", "T001", "<xml>test</xml>", "{}", "corr-1", documentType);
    }

    @BeforeEach
    void setUp() {
        // Set @Value fields via reflection
        ReflectionTestUtils.setField(orchestrationService, "maxRetries", 3);
    }

    @Nested
    @DisplayName("Process Signing Request - Success Path")
    class ProcessSigningRequestSuccess {

        @Test
        @DisplayName("Process new document with event document type - success")
        void testProcessNewDocumentSuccess() {
            // Setup - event has document type, so detection service is not called
            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
            when(signingService.signXml(any(), any())).thenReturn("<signed>xml</signed>");

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify document was saved 2 times (startSigning, markCompleted)
            verify(documentRepository, times(2)).save(any(SignedXmlDocument.class));

            // Verify signing was called
            verify(signingService).signXml(eq("<xml>test</xml>"), any(String.class));

            // Verify event was published
            ArgumentCaptor<XmlSignedEvent> eventCaptor = ArgumentCaptor.forClass(XmlSignedEvent.class);
            verify(eventPublisher).publishXmlSigned(eventCaptor.capture());

            // Verify published event fields
            XmlSignedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getDocumentId()).isNotNull();
            assertThat(publishedEvent.getInvoiceId()).isEqualTo("inv-001");
            assertThat(publishedEvent.getInvoiceNumber()).isEqualTo("T001");
            assertThat(publishedEvent.getSignedXmlContent()).isEqualTo("<signed>xml</signed>");
            assertThat(publishedEvent.getInvoiceDataJson()).isEqualTo("{}");
            assertThat(publishedEvent.getTransactionId()).isNotNull();
            assertThat(publishedEvent.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
        }

        @Test
        @DisplayName("Process with detected document type - success")
        void testProcessWithDetectedDocumentType() {
            // Setup: event has null documentType, detection returns TAX_INVOICE
            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
            when(signingService.signXml(any(), any())).thenReturn("<signed>xml</signed>");
            when(documentTypeDetectionService.detectFromXmlContent(any())).thenReturn(DocumentType.TAX_INVOICE);

            // Execute
            orchestrationService.processSigningRequest(createEvent(null));

            // Verify detection fallback was called
            verify(documentTypeDetectionService).detectFromXmlContent("<xml>test</xml>");

            // Verify event was published with TAX_INVOICE type (from detection)
            ArgumentCaptor<XmlSignedEvent> eventCaptor = ArgumentCaptor.forClass(XmlSignedEvent.class);
            verify(eventPublisher).publishXmlSigned(eventCaptor.capture());

            XmlSignedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        }

        @Test
        @DisplayName("Process already signed document - skips signing")
        void testProcessAlreadySignedSkips() {
            // Setup: existing document with status=COMPLETED (properly transitioned)
            SignedXmlDocument existingDoc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("inv-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build();
            existingDoc.startSigning();
            existingDoc.markCompleted("<signed/>", "txn-1", "cert", "XAdES-BASELINE-T");

            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.of(existingDoc));

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify signXml was never called
            verify(signingService, never()).signXml(any(), any());

            // Verify event was never published
            verify(eventPublisher, never()).publishXmlSigned(any());
        }

        @Test
        @DisplayName("Process when document type detection fails - exception is caught")
        void testProcessDocTypeDetectionFails() {
            // Setup: null documentType, detection returns null
            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
            when(documentTypeDetectionService.detectFromXmlContent(any())).thenReturn(null);

            // Execute - should not throw, error is handled
            orchestrationService.processSigningRequest(createEvent(null));

            // Verify findByInvoiceId was called (for error handling)
            verify(documentRepository, atLeastOnce()).findByInvoiceId(any());
        }

        @Test
        @DisplayName("Process when max retries exceeded - marks failed")
        void testProcessMaxRetriesExceeded() {
            // Setup: existing doc with retryCount=3 (maxRetries)
            SignedXmlDocument existingDoc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("inv-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .retryCount(3)
                    .build();

            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.of(existingDoc));

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify document was saved with failure message
            verify(documentRepository).save(argThat(savedDoc ->
                    savedDoc.getErrorMessage() != null &&
                    savedDoc.getErrorMessage().contains("Maximum retry")));

            // Verify signXml was never called
            verify(signingService, never()).signXml(any(), any());
        }

        @Test
        @DisplayName("Process when signing service throws - handles failure")
        void testProcessSigningServiceThrows() {
            // Setup: create a document first, then signing fails
            SignedXmlDocument existingDoc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("inv-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .status(SigningStatus.SIGNING)
                    .build();

            // First call finds no doc (for new document check), second call finds the doc (for failure handler)
            when(documentRepository.findByInvoiceId(any()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingDoc));
            when(signingService.signXml(any(), any())).thenThrow(new RuntimeException("CSC failed"));
            when(documentRepository.save(any())).thenReturn(existingDoc);

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify failure handler saved the document with error
            verify(documentRepository, atLeastOnce()).save(any(SignedXmlDocument.class));
        }

        @Test
        @DisplayName("Process signing failure with no existing document - creates failed doc")
        void testProcessSigningFailureNoExistingDoc() {
            // Setup: no existing document, signing fails
            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
            when(signingService.signXml(any(), any())).thenThrow(new RuntimeException("CSC failed"));

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify document was created (at least once for the initial save)
            verify(documentRepository, atLeastOnce()).save(any(SignedXmlDocument.class));
        }

        @Test
        @DisplayName("Process existing failed document for retry - reuses existing doc")
        void testProcessExistingFailedDocRetry() {
            // Setup: existing FAILED doc
            SignedXmlDocument existingDoc = SignedXmlDocument.builder()
                    .id(SignedXmlDocumentId.create())
                    .invoiceId("inv-001")
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .status(SigningStatus.FAILED)
                    .retryCount(1)
                    .build();

            when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.of(existingDoc));
            when(signingService.signXml(any(), any())).thenReturn("<signed>xml</signed>");

            // Execute
            orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

            // Verify save was called (startSigning and markCompleted)
            verify(documentRepository, atLeast(1)).save(any(SignedXmlDocument.class));

            // Verify signXml was called
            verify(signingService).signXml(eq("<xml>test</xml>"), any(String.class));
        }

        @Nested
        @DisplayName("Published Event Fields Verification")
        class PublishedEventFields {

            @Test
            @DisplayName("Verify all published event fields are correct")
            void testPublishedEventFields() {
                // Setup mocks - event has document type, so detection service is not called
                when(documentRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
                when(signingService.signXml(any(), any())).thenReturn("<signed>xml</signed>");

                // Execute
                orchestrationService.processSigningRequest(createEvent(DocumentType.TAX_INVOICE));

                // Verify event publisher was called
                ArgumentCaptor<XmlSignedEvent> eventCaptor = ArgumentCaptor.forClass(XmlSignedEvent.class);
                verify(eventPublisher).publishXmlSigned(eventCaptor.capture());

                // Verify all event fields
                XmlSignedEvent event = eventCaptor.getValue();
                assertThat(event.getDocumentId()).isNotNull();
                assertThat(event.getInvoiceId()).isEqualTo("inv-001");
                assertThat(event.getInvoiceNumber()).isEqualTo("T001");
                assertThat(event.getSignedXmlContent()).isEqualTo("<signed>xml</signed>");
                assertThat(event.getInvoiceDataJson()).isEqualTo("{}");
                assertThat(event.getTransactionId()).isNotNull();
                assertThat(event.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
                assertThat(event.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
            }
        }

        @Nested
        @DisplayName("Find By ID")
        class FindById {

            @Test
            @DisplayName("Find by valid ID returns document")
            void testFindByIdValid() {
                SignedXmlDocumentId id = SignedXmlDocumentId.create();
                SignedXmlDocument doc = SignedXmlDocument.builder()
                        .id(id)
                        .invoiceId("inv-001")
                        .invoiceNumber("T001")
                        .documentType(DocumentType.TAX_INVOICE)
                        .originalXml("<xml>test</xml>")
                        .build();

                when(documentRepository.findById(any(SignedXmlDocumentId.class))).thenReturn(Optional.of(doc));

                Optional<SignedXmlDocument> result = orchestrationService.findById(id.toString());

                assertThat(result).isPresent();
                result.ifPresent(foundDoc -> {
                    assertThat(foundDoc.getInvoiceId()).isEqualTo(doc.getInvoiceId());
                });
            }

            @Test
            @DisplayName("Find by invalid format returns empty")
            void testFindByIdInvalidFormat() {
                Optional<SignedXmlDocument> result = orchestrationService.findById("not-a-uuid");

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("Find by non-existent ID returns empty")
            void testFindByIdNotFound() {
                SignedXmlDocumentId id = SignedXmlDocumentId.create();

                when(documentRepository.findById(any())).thenReturn(Optional.empty());

                Optional<SignedXmlDocument> result = orchestrationService.findById(id.toString());

                assertThat(result).isEmpty();
            }
        }
    }

    /**
     * Helper to create a real domain object that mimics repository behavior.
     * Used to verify real domain methods (isSuccessful, etc.) are called.
     */
    private static class RealDomainDoc {
        static SignedXmlDocument forRepository(SignedXmlDocumentRepository repo, String invoiceId) {
            SignedXmlDocument doc = SignedXmlDocument.builder()
                    .invoiceId(invoiceId)
                    .invoiceNumber("T001")
                    .documentType(DocumentType.TAX_INVOICE)
                    .originalXml("<xml>test</xml>")
                    .build();
            when(repo.findByInvoiceId(invoiceId)).thenReturn(Optional.of(doc));
            return doc;
        }

        static boolean isSuccessful(SignedXmlDocument doc) {
            return doc.getStatus() == SigningStatus.COMPLETED;
        }
    }
}
