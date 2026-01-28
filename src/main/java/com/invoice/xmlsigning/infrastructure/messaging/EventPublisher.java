package com.invoice.xmlsigning.infrastructure.messaging;

import com.invoice.xmlsigning.domain.event.XmlSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for integration events using Apache Camel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    /**
     * Publish XML signed event
     */
    public void publishXmlSigned(XmlSignedEvent event) {
        log.info("Publishing XML signed event for invoice: {}", event.getInvoiceNumber());
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-xml-signed",
                event,
                "kafka.KEY",
                event.getInvoiceId()
            );
            log.info("Successfully published XML signed event: {}", event.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to publish XML signed event: {}", event.getInvoiceNumber(), e);
            throw e;
        }
    }
}
