package com.wpanther.xmlsigning.application.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XmlSignedEvent Tests")
class XmlSignedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should create event with convenience constructor")
    void shouldCreateEventWithConvenienceConstructor() {
        XmlSignedEvent event = new XmlSignedEvent(
            "doc-123", "INV-001", "INVOICE", "corr-456"
        );

        assertThat(event.getInvoiceId()).isEqualTo("doc-123");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(event.getDocumentType()).isEqualTo("INVOICE");
        assertThat(event.getCorrelationId()).isEqualTo("corr-456");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("xml.signed");
        assertThat(event.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create event with JsonCreator constructor")
    void shouldCreateEventWithJsonCreatorConstructor() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2024-01-15T10:30:00Z");

        XmlSignedEvent event = new XmlSignedEvent(
            eventId, occurredAt, "xml.signed", 1,
            "corr-456",             // sagaId (stores correlationId)
            "xml-signing-service",  // source
            "XML_SIGNED",           // traceType
            null,                   // context
            "doc-123", "INV-001", "TAX_INVOICE"
        );

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(event.getInvoiceId()).isEqualTo("doc-123");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(event.getCorrelationId()).isEqualTo("corr-456");
    }

    @Test
    @DisplayName("Should serialize and deserialize via JSON round-trip")
    void shouldSerializeAndDeserializeViaJsonRoundTrip() throws Exception {
        XmlSignedEvent original = new XmlSignedEvent(
            "doc-789", "INV-002", "RECEIPT", "corr-101"
        );

        String json = objectMapper.writeValueAsString(original);
        XmlSignedEvent deserialized = objectMapper.readValue(json, XmlSignedEvent.class);

        assertThat(deserialized.getEventId()).isEqualTo(original.getEventId());
        assertThat(deserialized.getInvoiceId()).isEqualTo("doc-789");
        assertThat(deserialized.getInvoiceNumber()).isEqualTo("INV-002");
        assertThat(deserialized.getDocumentType()).isEqualTo("RECEIPT");
        assertThat(deserialized.getCorrelationId()).isEqualTo("corr-101");
    }

    @Test
    @DisplayName("Should deserialize from JSON string")
    void shouldDeserializeFromJsonString() throws Exception {
        String json = """
            {
                "eventId": "00000000-0000-0000-0000-000000000001",
                "occurredAt": "2024-01-15T10:30:00Z",
                "eventType": "xml.signed",
                "version": 1,
                "sagaId": "corr-xyz",
                "source": "xml-signing-service",
                "traceType": "XML_SIGNED",
                "context": null,
                "invoiceId": "doc-abc",
                "invoiceNumber": "INV-100",
                "documentType": "INVOICE"
            }
            """;

        XmlSignedEvent event = objectMapper.readValue(json, XmlSignedEvent.class);

        assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(event.getInvoiceId()).isEqualTo("doc-abc");
        assertThat(event.getInvoiceNumber()).isEqualTo("INV-100");
        assertThat(event.getDocumentType()).isEqualTo("INVOICE");
        assertThat(event.getCorrelationId()).isEqualTo("corr-xyz");
    }
}
