package com.wpanther.xmlsigning.domain.port.out;

import com.wpanther.xmlsigning.application.dto.event.XmlSignedEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound port for publishing signed XML events to notification-service.
 * Events are published via the transactional outbox pattern for exactly-once delivery.
 * Implementations MUST require an active transaction (@Transactional(MANDATORY)).
 */
public interface XmlSignedEventPort {

    /**
     * Publish a signed XML event.
     *
     * @param event the event to publish
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void publishXmlSigned(XmlSignedEvent event);
}
