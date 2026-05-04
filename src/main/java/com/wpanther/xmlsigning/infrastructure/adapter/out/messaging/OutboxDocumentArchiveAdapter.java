package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.application.dto.event.DocumentArchiveEvent;
import com.wpanther.xmlsigning.application.port.out.DocumentArchivePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outbox-based adapter for publishing document archive events.
 * Implements DocumentArchivePort by writing events to the transactional outbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDocumentArchiveAdapter implements DocumentArchivePort {

    private static final String AGGREGATE_TYPE = "SignedXmlDocument";
    private static final String TOPIC = "document.archive";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DocumentArchiveEvent event) {
        if (event.getSourceUrl() == null || event.getSourceUrl().isBlank()) {
            throw new IllegalStateException(
                    "Refusing to publish DocumentArchiveEvent with null/blank sourceUrl for documentId="
                    + event.getDocumentId() + " — upload must precede outbox emission.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("correlationId", event.getCorrelationId());
        headers.put("artifactType", event.getArtifactType());
        headers.put("documentNumber", event.getDocumentNumber());

        outboxService.saveWithRouting(
                event,
                AGGREGATE_TYPE,
                event.getDocumentId(),
                TOPIC,
                event.getDocumentId(),
                toJson(headers));

        log.info("Published DocumentArchiveEvent: documentId={}, artifactType={}",
                event.getDocumentId(), event.getArtifactType());
    }

    /**
     * Serializes headers map to JSON for outbox storage.
     * <p>
     * This method runs within a transaction context, so any exception will
     * trigger transaction rollback and ensure data consistency.
     *
     * @param map the headers map to serialize
     * @return JSON string representation
     * @throws IllegalStateException if JSON serialization fails
     */
    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize document.archive headers to JSON: {}", map, e);
            throw new IllegalStateException(
                    "Cannot serialize document.archive headers — transaction aborted. Headers: " + map, e);
        }
    }
}
