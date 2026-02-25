package com.wpanther.xmlsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.xmlsigning.domain.event.XmlSigningReplyEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Publishes saga reply events via outbox pattern.
 * Replies are sent to orchestrator via saga.reply.xml-signing topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyPublisher {

    private static final String REPLY_TOPIC = "saga.reply.xml-signing";
    private static final String AGGREGATE_TYPE = "SignedXmlDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                               String signedXmlUrl, Long signedXmlSize) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.success(sagaId, sagaStep, correlationId,
                signedXmlUrl, signedXmlSize);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published SUCCESS saga reply for saga {} step {} with signedXmlUrl={}",
                sagaId, sagaStep, signedXmlUrl);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "FAILURE"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published FAILURE saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for saga {} step {}", sagaId, sagaStep);
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
            log.error("Failed to serialize outbox headers to JSON: {}", map, e);
            throw new IllegalStateException(
                    "Cannot serialize outbox headers - transaction aborted. Headers: " + map, e);
        }
    }
}
