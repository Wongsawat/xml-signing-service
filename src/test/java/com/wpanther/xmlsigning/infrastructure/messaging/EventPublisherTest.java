package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EventPublisher Tests")
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(outboxService, objectMapper);
    }

    @Test
    @DisplayName("Should publish XmlSignedEvent with correct arguments")
    void testPublishXmlSignedSuccess() throws Exception {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-123", "INV-001", "INVOICE", "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"invoiceNumber\":\"INV-001\"}");

        eventPublisher.publishXmlSigned(event);

        verify(outboxService).saveWithRouting(
            eq(event),
            eq("SignedXmlDocument"),
            eq("doc-123"),
            eq("xml.signed"),
            eq("doc-123"),
            eq("{\"correlationId\":\"corr-123\",\"invoiceNumber\":\"INV-001\"}")
        );
    }

    @Test
    @DisplayName("Should include correlationId and invoiceNumber in headers")
    void testPublishXmlSignedHeaderContent() throws Exception {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-123", "INV-001", "TAX_INVOICE", "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"invoiceNumber\":\"INV-001\"}");

        eventPublisher.publishXmlSigned(event);

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(), any(), headersCaptor.capture()
        );

        String headers = headersCaptor.getValue();
        assertTrue(headers.contains("corr-123"));
        assertTrue(headers.contains("INV-001"));
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void testToJsonError() throws Exception {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-123", "INV-001", "INVOICE", "corr-123"
        );

        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("JSON error") {});

        eventPublisher.publishXmlSigned(event);

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(), any(), headersCaptor.capture()
        );

        assertNull(headersCaptor.getValue());
    }

    @Test
    @DisplayName("Should use correct topic 'xml.signed'")
    void testPublishUsesCorrectTopic() throws Exception {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-123", "INV-001", "INVOICE", "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        eventPublisher.publishXmlSigned(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());
        assertEquals("xml.signed", topicCaptor.getValue());
    }

    @Test
    @DisplayName("Should use invoiceId as partition key")
    void testPublishUsesInvoiceIdAsPartitionKey() throws Exception {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-456", "INV-002", "TAX_INVOICE", "corr-456"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        eventPublisher.publishXmlSigned(event);

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), partitionKeyCaptor.capture(), any());
        assertEquals("doc-456", partitionKeyCaptor.getValue());
    }
}
