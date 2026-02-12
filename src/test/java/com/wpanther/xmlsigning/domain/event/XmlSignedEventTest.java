package com.wpanther.xmlsigning.domain.event;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link XmlSignedEvent}.
 */
@DisplayName("XmlSignedEvent")
class XmlSignedEventTest {

    @Nested
    @DisplayName("Simple Constructor")
    class SimpleConstructor {

        @Test
        @DisplayName("Creates event with all fields")
        void testSimpleConstructor() {
            // Execute
            XmlSignedEvent event = new XmlSignedEvent(
                    "doc-1", "inv-1", "T001",
                    "<signed>xml</signed>", "{}",
                    "txn-1", "cert-data", "XAdES-BASELINE-T",
                    "corr-1", DocumentType.TAX_INVOICE
            );

            // Verify
            assertThat(event.getDocumentId()).isEqualTo("doc-1");
            assertThat(event.getInvoiceId()).isEqualTo("inv-1");
            assertThat(event.getInvoiceNumber()).isEqualTo("T001");
            assertThat(event.getSignedXmlContent()).isEqualTo("<signed>xml</signed>");
            assertThat(event.getInvoiceDataJson()).isEqualTo("{}");
            assertThat(event.getTransactionId()).isEqualTo("txn-1");
            assertThat(event.getCertificate()).isEqualTo("cert-data");
            assertThat(event.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(event.getCorrelationId()).isEqualTo("corr-1");
            assertThat(event.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);

            // Inherited fields
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
        }

        @Test
        @DisplayName("Generates unique event IDs")
        void testEventIdUniqueness() {
            XmlSignedEvent event1 = new XmlSignedEvent(
                    "doc-1", "inv-1", "T001", "<signed/>", "{}",
                    "txn-1", "cert", "XAdES-BASELINE-T", "corr-1", DocumentType.TAX_INVOICE);

            XmlSignedEvent event2 = new XmlSignedEvent(
                    "doc-2", "inv-2", "T002", "<signed/>", "{}",
                    "txn-2", "cert", "XAdES-BASELINE-T", "corr-2", DocumentType.INVOICE);

            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }

        @Test
        @DisplayName("Allows null optional fields")
        void testNullOptionalFields() {
            XmlSignedEvent event = new XmlSignedEvent(
                    "doc-1", "inv-1", "T001",
                    "<signed/>", "{}",
                    null, null, null,
                    null, null
            );

            assertThat(event.getTransactionId()).isNull();
            assertThat(event.getCertificate()).isNull();
            assertThat(event.getSignatureLevel()).isNull();
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getDocumentType()).isNull();
        }
    }

    @Nested
    @DisplayName("JsonCreator Constructor")
    class JsonCreatorConstructor {

        @Test
        @DisplayName("Creates event with explicit event metadata")
        void testJsonCreatorConstructor() {
            UUID eventId = UUID.randomUUID();
            Instant occurredAt = Instant.now();
            String eventType = "custom.event.type";
            int version = 2;

            // Execute
            XmlSignedEvent event = new XmlSignedEvent(
                    eventId, occurredAt, eventType, version,
                    "doc-1", "inv-1", "T001",
                    "<signed>xml</signed>", "{}",
                    "txn-1", "cert-data", "XAdES-BASELINE-T",
                    "corr-1", DocumentType.TAX_INVOICE
            );

            // Verify event metadata
            assertThat(event.getEventId()).isEqualTo(eventId);
            assertThat(event.getOccurredAt()).isEqualTo(occurredAt);

            // Verify all fields
            assertThat(event.getDocumentId()).isEqualTo("doc-1");
            assertThat(event.getInvoiceId()).isEqualTo("inv-1");
            assertThat(event.getInvoiceNumber()).isEqualTo("T001");
            assertThat(event.getSignedXmlContent()).isEqualTo("<signed>xml</signed>");
            assertThat(event.getInvoiceDataJson()).isEqualTo("{}");
            assertThat(event.getTransactionId()).isEqualTo("txn-1");
            assertThat(event.getCertificate()).isEqualTo("cert-data");
            assertThat(event.getSignatureLevel()).isEqualTo("XAdES-BASELINE-T");
            assertThat(event.getCorrelationId()).isEqualTo("corr-1");
            assertThat(event.getDocumentType()).isEqualTo(DocumentType.TAX_INVOICE);
        }
    }

    @Nested
    @DisplayName("All Document Types")
    class AllDocumentTypes {

        @Test
        @DisplayName("Supports all document types")
        void testAllDocumentTypes() {
            DocumentType[] types = DocumentType.values();

            for (DocumentType type : types) {
                XmlSignedEvent event = new XmlSignedEvent(
                        "doc-1", "inv-1", "T001",
                        "<signed/>", "{}",
                        "txn-1", "cert", "XAdES-BASELINE-T",
                        "corr-1", type
                );

                assertThat(event.getDocumentType()).isEqualTo(type);
            }
        }
    }
}
