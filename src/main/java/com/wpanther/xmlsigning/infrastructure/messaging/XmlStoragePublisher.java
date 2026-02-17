package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlStorageRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes XmlStorageRequestedEvent to the outbox for CDC-based storage integration.
 * The outbox pattern ensures exactly-once delivery with Debezium CDC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XmlStoragePublisher {

    private static final String TOPIC = "xml.storage.requested";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a storage request event to the outbox.
     * Must be called within an existing transaction (MANDATORY propagation).
     *
     * @param event The storage request event to publish
     * @param aggregateId The aggregate ID for the outbox entry
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishStorageRequest(XmlStorageRequestedEvent event, String aggregateId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", event.getCorrelationId());
        headers.put("documentType", event.getDocumentType());
        if (event.getInvoiceNumber() != null) {
            headers.put("invoiceNumber", event.getInvoiceNumber());
        }

        outboxService.saveWithRouting(
            event,
            "SignedXmlDocument",
            aggregateId,
            TOPIC,
            event.getInvoiceId(),
            toJson(headers)
        );

        log.info("Published XmlStorageRequestedEvent to outbox for invoice: {}", event.getInvoiceId());
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
