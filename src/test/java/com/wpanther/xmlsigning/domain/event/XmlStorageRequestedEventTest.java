package com.wpanther.xmlsigning.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XmlStorageRequestedEvent Tests")
class XmlStorageRequestedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should create event with convenience constructor")
    void shouldCreateEventWithConvenienceConstructor() {
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>test</root>";

        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", "INV-001", "corr-456"
        );

        assertThat(event.getInvoiceId()).isEqualTo("doc-123");
        assertThat(event.getXmlContent()).isEqualTo(xmlContent);
        assertThat(event.getDocumentType()).isEqualTo("INVOICE");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(event.getCorrelationId()).isEqualTo("corr-456");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("XmlStorageRequestedEvent");
        assertThat(event.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create event with JsonCreator constructor")
    void shouldCreateEventWithJsonCreatorConstructor() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");
        String xmlContent = "<?xml version=\"1.0\"?><test>Data</test>";

        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            eventId, occurredAt, "XmlStorageRequestedEvent", 1,
            "doc-123", xmlContent, "TAX_INVOICE", "INV-001", "corr-456"
        );

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(event.getInvoiceId()).isEqualTo("doc-123");
        assertThat(event.getXmlContent()).isEqualTo(xmlContent);
        assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(event.getCorrelationId()).isEqualTo("corr-456");
    }

    @Test
    @DisplayName("Should serialize and deserialize via JSON round-trip")
    void shouldSerializeAndDeserializeViaJsonRoundTrip() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><invoice>content</invoice>";

        XmlStorageRequestedEvent original = new XmlStorageRequestedEvent(
            "doc-789", xmlContent, "RECEIPT", "INV-002", "corr-101"
        );

        String json = objectMapper.writeValueAsString(original);
        XmlStorageRequestedEvent deserialized = objectMapper.readValue(json, XmlStorageRequestedEvent.class);

        assertThat(deserialized.getEventId()).isEqualTo(original.getEventId());
        assertThat(deserialized.getInvoiceId()).isEqualTo("doc-789");
        assertThat(deserialized.getXmlContent()).isEqualTo(xmlContent);
        assertThat(deserialized.getDocumentType()).isEqualTo("RECEIPT");
        assertThat(deserialized.getInvoiceNumber()).isEqualTo("INV-002");
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-101");
    }

    @Test
    @DisplayName("Should deserialize from JSON string")
    void shouldDeserializeFromJsonString() throws Exception {
        String xmlContent = "<?xml version=\"1.0\"?><taxinvoice>data</taxinvoice>";

        String json = """
            {
                "eventId": "00000000-0000-0000-0000-000000000001",
                "occurredAt": "2024-01-15T10:30:00Z",
                "eventType": "XmlStorageRequestedEvent",
                "version": 1,
                "invoiceId": "doc-abc",
                "xmlContent": "<?xml version=\\"1.0\\"?><taxinvoice>data</taxinvoice>",
                "documentType": "TAX_INVOICE",
                "invoiceNumber": "INV-100",
                "correlationId": "corr-xyz"
            }
            """;

        XmlStorageRequestedEvent event = objectMapper.readValue(json, XmlStorageRequestedEvent.class);

        assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(event.getInvoiceId()).isEqualTo("doc-abc");
        assertThat(event.getXmlContent()).isEqualTo(xmlContent);
        assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-100");
        assertThat(event.getCorrelationId()).isEqualTo("corr-xyz");
    }

    @Test
    @DisplayName("Should handle null invoice number")
    void shouldHandleNullInvoiceNumber() {
        String xmlContent = "<?xml version=\"1.0\"?><root>test</root>";

        XmlStorageRequestedEvent event = new XmlStorageRequestedEvent(
            "doc-123", xmlContent, "INVOICE", null, "corr-456"
        );

        assertThat(event.getInvoiceNumber()).isNull();
        assertThat(event.getInvoiceId()).isEqualTo("doc-123");
        assertThat(event.getXmlContent()).isEqualTo(xmlContent);
    }
}
