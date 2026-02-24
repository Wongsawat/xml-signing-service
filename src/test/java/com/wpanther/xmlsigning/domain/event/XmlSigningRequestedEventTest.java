package com.wpanther.xmlsigning.domain.event;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link XmlSigningRequestedEvent}.
 * Tests both constructors (simple and @JsonCreator) and getter methods.
 */
@DisplayName("XmlSigningRequestedEvent")
class XmlSigningRequestedEventTest {

    @Nested
    @DisplayName("Simple Constructor")
    class SimpleConstructor {

        @Test
        @DisplayName("Simple constructor sets all fields and generates metadata")
        void testSimpleConstructor() {
            XmlSigningRequestedEvent event = new XmlSigningRequestedEvent(
                    "inv-001", "T001", "<xml>test</xml>", "{}", "corr-1", DocumentType.TAX_INVOICE);

            assertThat(event.getInvoiceId()).isEqualTo("inv-001");
            assertThat(event.getInvoiceNumber()).isEqualTo("T001");
            assertThat(event.getXmlContent()).isEqualTo("<xml>test</xml>");
            assertThat(event.getInvoiceDataJson()).isEqualTo("{}");
            assertThat(event.getCorrelationId()).isEqualTo("corr-1");
            assertThat(event.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getEventType()).isEqualTo("XmlSigningRequestedEvent");
            assertThat(event.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("JsonCreator constructor with explicit fields overrides auto-generated values")
        void testJsonCreatorConstructor() {
            UUID eventId = UUID.randomUUID();
            Instant occurredAt = Instant.now();

            XmlSigningRequestedEvent event = new XmlSigningRequestedEvent(
                    eventId, occurredAt, "custom", 2,
                    "corr-2",              // sagaId (stores correlationId)
                    "xml-signing-service", // source
                    "XML_SIGNING_REQUESTED", // traceType
                    null,                  // context
                    "inv-002", "T002", "<xml>test2</xml>", "{}", null);

            assertThat(event.getEventId()).isEqualTo(eventId);
            assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
            assertThat(event.getEventType()).isEqualTo("custom");
            assertThat(event.getVersion()).isEqualTo(2);
            assertThat(event.getInvoiceId()).isEqualTo("inv-002");
            assertThat(event.getInvoiceNumber()).isEqualTo("T002");
            assertThat(event.getXmlContent()).isEqualTo("<xml>test2</xml>");
            assertThat(event.getInvoiceDataJson()).isEqualTo("{}");
            assertThat(event.getCorrelationId()).isEqualTo("corr-2");
            assertThat(event.getDocumentType()).isNull();
            assertThat(event.getEventId()).isEqualTo(eventId);
        }

        @Test
        @DisplayName("EventId uniqueness - two events have different IDs")
        void testEventIdUniqueness() {
            XmlSigningRequestedEvent event1 = new XmlSigningRequestedEvent(
                    "inv-001", "T001", "<xml>test</xml>", "{}", "corr-1", DocumentType.TAX_INVOICE);

            // Wait to ensure different timestamps
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            XmlSigningRequestedEvent event2 = new XmlSigningRequestedEvent(
                    "inv-001", "T001", "<xml>test</xml>", "{}", "corr-1", DocumentType.TAX_INVOICE);

            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }
    }
}
