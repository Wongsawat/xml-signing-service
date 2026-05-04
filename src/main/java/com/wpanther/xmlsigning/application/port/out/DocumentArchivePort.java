package com.wpanther.xmlsigning.application.port.out;

import com.wpanther.xmlsigning.application.dto.event.DocumentArchiveEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound port for publishing archival notifications to document-storage-service.
 * Events are published via the transactional outbox pattern for exactly-once delivery.
 * Implementations MUST require an active transaction (@Transactional(MANDATORY)).
 */
public interface DocumentArchivePort {

    /**
     * Publish a document archive event.
     *
     * @param event the event to publish
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void publish(DocumentArchiveEvent event);
}
