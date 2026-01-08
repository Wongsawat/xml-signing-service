package com.invoice.xmlsigning.infrastructure.messaging;

import com.invoice.xmlsigning.domain.event.IntegrationEvent;
import com.invoice.xmlsigning.domain.event.XmlSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher for integration events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, IntegrationEvent> kafkaTemplate;

    @Value("${app.kafka.topics.xml-signed}")
    private String xmlSignedTopic;

    /**
     * Publish XML signed event
     */
    public void publishXmlSigned(XmlSignedEvent event) {
        log.info("Publishing XML signed event for invoice: {}", event.getInvoiceNumber());

        CompletableFuture<SendResult<String, IntegrationEvent>> future =
            kafkaTemplate.send(xmlSignedTopic, event.getInvoiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published XML signed event for invoice: {}", event.getInvoiceNumber());
            } else {
                log.error("Failed to publish XML signed event for invoice: {}", event.getInvoiceNumber(), ex);
            }
        });
    }
}
