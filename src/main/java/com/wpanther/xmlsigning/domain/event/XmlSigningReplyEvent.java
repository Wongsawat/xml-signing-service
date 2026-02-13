package com.wpanther.xmlsigning.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Concrete SagaReply for xml-signing-service.
 * Published to Kafka topic: saga.reply.xml-signing
 */
public class XmlSigningReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    /**
     * Create a SUCCESS reply.
     */
    public static XmlSigningReplyEvent success(String sagaId, String sagaStep, String correlationId) {
        return new XmlSigningReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    /**
     * Create a FAILURE reply.
     */
    public static XmlSigningReplyEvent failure(String sagaId, String sagaStep, String correlationId,
                                               String errorMessage) {
        return new XmlSigningReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Create a COMPENSATED reply.
     */
    public static XmlSigningReplyEvent compensated(String sagaId, String sagaStep, String correlationId) {
        return new XmlSigningReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    // For SUCCESS and COMPENSATED (delegates to SagaReply 4-arg status constructor)
    private XmlSigningReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    // For FAILURE (delegates to SagaReply 4-arg error constructor)
    private XmlSigningReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
