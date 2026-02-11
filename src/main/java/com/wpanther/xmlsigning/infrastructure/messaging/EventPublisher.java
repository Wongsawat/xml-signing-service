package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for integration events using the outbox pattern.
 * <p>
 * Events are written to the outbox table within the same transaction as domain state changes.
 * Debezium CDC reads the outbox table and publishes events to Kafka topics asynchronously.
 * This provides guaranteed delivery and prevents event loss during failures.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Publish XML signed event via the outbox pattern.
     * The event is written to the outbox_events table in the same transaction
     * as the SignedXmlDocument state change. Debezium CDC will pick it up
     * and route it to the appropriate type-specific Kafka topic.
     * <p>
     * Must be called within an existing transaction (MANDATORY propagation).
     *
     * @param event the XML signed event to publish
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishXmlSigned(XmlSignedEvent event) {
        log.info("Publishing XML signed event to outbox for invoice: {}, documentType: {}",
            event.getInvoiceNumber(), event.getDocumentType());

        // Determine the target Kafka topic from the document type
        String topic = event.getDocumentType() != null
            ? event.getDocumentType().getKafkaTopic()
            : "xml.signing.dlq";

        // Build headers for Debezium EventRouter
        Map<String, String> headers = new HashMap<>();
        if (event.getCorrelationId() != null) {
            headers.put("correlationId", event.getCorrelationId());
        }
        if (event.getDocumentType() != null) {
            headers.put("documentType", event.getDocumentType().name());
        }

        String headersJson = toJson(headers);

        // Use partition key for ordering: correlationId if available, else invoiceId
        String partitionKey = event.getCorrelationId() != null
            ? event.getCorrelationId()
            : event.getInvoiceId();

        outboxService.saveWithRouting(
            event,
            "SignedXmlDocument",    // aggregateType
            event.getInvoiceId(),   // aggregateId
            topic,                  // Kafka topic (from DocumentType.getKafkaTopic())
            partitionKey,           // partitionKey (for Kafka message ordering)
            headersJson             // headers (as JSON)
        );

        log.info("Successfully saved XML signed event to outbox: {}, topic: {}, documentType: {}",
            event.getInvoiceNumber(), topic, event.getDocumentType());
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }
}
