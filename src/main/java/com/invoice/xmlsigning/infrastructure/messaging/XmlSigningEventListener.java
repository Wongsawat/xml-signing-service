package com.invoice.xmlsigning.infrastructure.messaging;

import com.invoice.xmlsigning.application.service.XmlSigningOrchestrationService;
import com.invoice.xmlsigning.domain.event.XmlSigningRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for XML signing events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XmlSigningEventListener {

    private final XmlSigningOrchestrationService orchestrationService;

    @KafkaListener(
        topics = "${app.kafka.topics.xml-signing-requested}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleXmlSigningRequest(
        @Payload XmlSigningRequestedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        log.info("Received XML signing request (partition: {}, offset: {})", partition, offset);
        log.debug("Processing XML signing request for invoice: {}", event.getInvoiceNumber());

        try {
            // Process signing request
            orchestrationService.processSigningRequest(event);

            // Acknowledge message after successful processing
            acknowledgment.acknowledge();
            log.info("Successfully processed XML signing request for invoice: {}", event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Error processing XML signing request for invoice: {}", event.getInvoiceNumber(), e);
            // Don't acknowledge - message will be retried
        }
    }
}
