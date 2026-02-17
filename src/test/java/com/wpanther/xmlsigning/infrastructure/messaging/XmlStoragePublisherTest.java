package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlStorageRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("XmlStoragePublisher Tests")
@ExtendWith(MockitoExtension.class)
class XmlStoragePublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    private XmlStoragePublisher xmlStoragePublisher;

    @BeforeEach
    void setUp() {
        xmlStoragePublisher = new XmlStoragePublisher(outboxService, objectMapper);
    }

    @Test
    @DisplayName("Should publish XmlStorageRequestedEvent with correct arguments")
    void testPublishStorageRequestSuccess() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", "INV-001", "corr-123"
        );
        String aggregateId = UUID.randomUUID().toString();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"documentType\":\"INVOICE\",\"invoiceNumber\":\"INV-001\"}");

        xmlStoragePublisher.publishStorageRequest(event, aggregateId);

        verify(outboxService).saveWithRouting(
            eq(event),
            eq("SignedXmlDocument"),
            eq(aggregateId),
            eq("xml.storage.requested"),
            eq("doc-123"),
            eq("{\"correlationId\":\"corr-123\",\"documentType\":\"INVOICE\",\"invoiceNumber\":\"INV-001\"}")
        );
    }

    @Test
    @DisplayName("Should include all headers including invoiceNumber")
    void testPublishStorageRequestHeaderContentWithInvoiceNumber() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "TAX_INVOICE", "INV-001", "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"documentType\":\"TAX_INVOICE\",\"invoiceNumber\":\"INV-001\"}");

        xmlStoragePublisher.publishStorageRequest(event, "aggregate-123");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(), any(), headersCaptor.capture()
        );

        String headers = headersCaptor.getValue();
        assertTrue(headers.contains("corr-123"));
        assertTrue(headers.contains("TAX_INVOICE"));
        assertTrue(headers.contains("INV-001"));
    }

    @Test
    @DisplayName("Should exclude invoiceNumber from headers when null")
    void testPublishStorageRequestHeaderContentWithoutInvoiceNumber() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", null, "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"correlationId\":\"corr-123\",\"documentType\":\"INVOICE\"}");

        xmlStoragePublisher.publishStorageRequest(event, "aggregate-123");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(), any(), headersCaptor.capture()
        );

        String headers = headersCaptor.getValue();
        assertTrue(headers.contains("corr-123"));
        assertTrue(headers.contains("INVOICE"));
        assertFalse(headers.contains("invoiceNumber"));
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void testToJsonError() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", "INV-001", "corr-123"
        );

        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("JSON error") {});

        xmlStoragePublisher.publishStorageRequest(event, "aggregate-123");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
            any(), any(), any(), any(), any(), headersCaptor.capture()
        );

        assertNull(headersCaptor.getValue());
    }

    @Test
    @DisplayName("Should use correct topic 'xml.storage.requested'")
    void testPublishUsesCorrectTopic() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", "INV-001", "corr-123"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        xmlStoragePublisher.publishStorageRequest(event, "aggregate-123");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());
        assertEquals("xml.storage.requested", topicCaptor.getValue());
    }

    @Test
    @DisplayName("Should use invoiceId as partition key")
    void testPublishUsesInvoiceIdAsPartitionKey() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";
        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-456", xmlContent, "TAX_INVOICE", "INV-002", "corr-456"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        xmlStoragePublisher.publishStorageRequest(event, "aggregate-123");

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), partitionKeyCaptor.capture(), any());
        assertEquals("doc-456", partitionKeyCaptor.getValue());
    }
}
