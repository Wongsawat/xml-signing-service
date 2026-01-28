package com.invoice.xmlsigning.infrastructure.messaging;

import com.invoice.xmlsigning.domain.event.XmlSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
        log.info("Publishing XML signed event for invoice: {}, documentType: {}",
            event.getInvoiceNumber(), event.getDocumentType());
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("kafka.KEY", event.getInvoiceId());
            headers.put("documentType", event.getDocumentType() != null ? event.getDocumentType().name() : null);

            producerTemplate.sendBodyAndHeaders("direct:publish-xml-signed", event, headers);

            log.info("Successfully published XML signed event: {}, documentType: {}",
                event.getInvoiceNumber(), event.getDocumentType());
        } catch (Exception e) {
            log.error("Failed to publish XML signed event: {}", event.getInvoiceNumber(), e);
            throw e;
        }
    }
}
