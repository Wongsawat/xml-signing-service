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

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishXmlSigned(XmlSignedEvent event) {
        Map<String, String> headers = Map.of(
            "correlationId", event.getCorrelationId(),
            "invoiceNumber", event.getInvoiceNumber()
        );

        outboxService.saveWithRouting(
            event,
            "SignedXmlDocument",
            event.getInvoiceId(),
            "xml.signed",
            event.getInvoiceId(),
            toJson(headers)
        );

        log.info("Published XmlSignedEvent to outbox: {}", event.getInvoiceNumber());
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
