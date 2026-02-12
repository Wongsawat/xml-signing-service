package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventPublisher}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher")
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EventPublisher eventPublisher;

    private XmlSignedEvent createEvent(DocumentType documentType, String correlationId) {
        return new XmlSignedEvent(
                "doc-1", "inv-1", "T001",
                "<signed>xml</signed>", "{}",
                "txn-1", "cert", "XAdES-BASELINE-T",
                correlationId, documentType
        );
    }

    @Nested
    @DisplayName("publishXmlSigned() Method")
    class PublishXmlSignedMethod {

        @Test
        @DisplayName("Publishes event with document type routing")
        void testPublishWithDocumentType() {
            // Setup
            XmlSignedEvent event = createEvent(DocumentType.TAX_INVOICE, "corr-1");

            // Execute
            eventPublisher.publishXmlSigned(event);

            // Verify outbox service was called with correct topic
            verify(outboxService).saveWithRouting(
                    eq(event),
                    eq("SignedXmlDocument"),
                    eq("inv-1"),
                    eq("xml.signed.tax-invoice"),
                    eq("corr-1"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Routes to DLQ when document type is null")
        void testPublishNullDocumentType() {
            // Setup
            XmlSignedEvent event = createEvent(null, "corr-1");

            // Execute
            eventPublisher.publishXmlSigned(event);

            // Verify topic is DLQ
            verify(outboxService).saveWithRouting(
                    eq(event),
                    eq("SignedXmlDocument"),
                    eq("inv-1"),
                    eq("xml.signing.dlq"),
                    eq("corr-1"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Uses correlationId as partition key when available")
        void testCorrelationIdAsPartitionKey() {
            // Setup
            XmlSignedEvent event = createEvent(DocumentType.INVOICE, "corr-123");

            // Execute
            eventPublisher.publishXmlSigned(event);

            // Verify
            verify(outboxService).saveWithRouting(
                    any(),
                    anyString(),
                    anyString(),
                    anyString(),
                    eq("corr-123"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Uses invoiceId as partition key when correlationId is null")
        void testInvoiceIdAsPartitionKey() {
            // Setup
            XmlSignedEvent event = createEvent(DocumentType.INVOICE, null);

            // Execute
            eventPublisher.publishXmlSigned(event);

            // Verify
            verify(outboxService).saveWithRouting(
                    any(),
                    anyString(),
                    eq("inv-1"),
                    anyString(),
                    eq("inv-1"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Includes documentType in headers")
        void testHeadersContainDocumentType() throws JsonProcessingException {
            // Setup
            XmlSignedEvent event = createEvent(DocumentType.TAX_INVOICE, "corr-1");

            // Execute
            eventPublisher.publishXmlSigned(event);

            // Capture and verify headers
            ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).saveWithRouting(
                    any(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    headersCaptor.capture()
            );

            String headersJson = headersCaptor.getValue();
            assertThat(headersJson).contains("documentType");
            assertThat(headersJson).contains("TAX_INVOICE");
            assertThat(headersJson).contains("correlationId");
        }

        @Test
        @DisplayName("Routes to correct topic for each document type")
        void testAllDocumentTypeTopics() {
            // Test all document types
            DocumentType[] types = DocumentType.values();

            for (DocumentType type : types) {
                // Setup
                XmlSignedEvent event = createEvent(type, "corr-1");

                // Execute
                eventPublisher.publishXmlSigned(event);

                // Verify correct topic
                verify(outboxService).saveWithRouting(
                        any(),
                        anyString(),
                        anyString(),
                        eq(type.getKafkaTopic()),
                        anyString(),
                        anyString()
                );
            }
        }
    }
}
