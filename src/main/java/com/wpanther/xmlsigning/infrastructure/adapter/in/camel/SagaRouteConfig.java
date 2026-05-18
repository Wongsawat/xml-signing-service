package com.wpanther.xmlsigning.infrastructure.adapter.in.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.application.usecase.SagaCommandPort;
import com.wpanther.xmlsigning.infrastructure.messaging.CommandValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Apache Camel routes for saga command and compensation consumers.
 * Saga-only mode: replaces old xml.signing.requested topic consumption.
 */
@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandPort sagaCommandPort;
    private final CommandValidator commandValidator;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-xml-signing}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-xml-signing}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:xml.signing.dlq}")
    private String dlqTopic;

    @Value("${app.kafka.consumers-count:3}")
    private int consumersCount;

    /**
     * Builds common Kafka consumer URL parameters.
     * Used by both saga command and compensation consumers.
     *
     * @param topic the Kafka topic name
     * @return the Kafka endpoint URL with common parameters
     */
    private String kafkaConsumerUrl(String topic) {
        return "kafka:" + topic
                + "?brokers=" + kafkaBrokers
                + "&groupId=xml-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=" + consumersCount;
    }

    public SagaRouteConfig(SagaCommandPort sagaCommandPort, CommandValidator commandValidator, ObjectMapper objectMapper) {
        this.sagaCommandPort = sagaCommandPort;
        this.commandValidator = commandValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        // DLQ uses Kafka producer configuration with explicit serializers to ensure
        // proper serialization and header preservation for debugging
        errorHandler(deadLetterChannel("kafka:" + dlqTopic
                        + "?brokers=" + kafkaBrokers
                        + "&keySerializer=org.apache.kafka.common.serialization.StringSerializer"
                        + "&valueSerializer=org.apache.kafka.common.serialization.StringSerializer")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(1000)
                        .useExponentialBackOff()
                        .backOffMultiplier(2)
                        .maximumRedeliveryDelay(10000)
                        .logExhausted(true)
                        .logStackTrace(true));

        // ============================================================
        // CONSUMER ROUTE: saga.command.xml-signing (from orchestrator)
        // ============================================================
        from(kafkaConsumerUrl(sagaCommandTopic))
                        .routeId("saga-command-consumer")
                        .log(">>> RECEIVED raw Kafka message: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}, key=${header[kafka.KEY]}")
                        .process(exchange -> {
                            log.info(">>> ABOUT TO UNMARSHAL message at offset={}", exchange.getMessage().getHeader("kafka.OFFSET"));
                        })
                        // Manual Jackson unmarshal — bypasses Camel's opaque exception handling
                        .process(exchange -> {
                            try {
                                String body = exchange.getIn().getBody(String.class);
                                ProcessXmlSigningCommand cmd = objectMapper.readValue(body, ProcessXmlSigningCommand.class);
                                exchange.getIn().setBody(cmd);
                                log.info(">>> [UNMARSHAL] sagaId={}, documentId={}, documentNumber={}",
                                        cmd.getSagaId(), cmd.getDocumentId(), cmd.getDocumentNumber());
                            } catch (Exception e) {
                                log.error(">>> [UNMARSHAL] FAILED: {} — {}", e.getClass().getName(), e.getMessage(), e);
                                throw e;
                            }
                        })
                        .process(commandValidator)  // Validate command before processing
                        .process(exchange -> {
                                ProcessXmlSigningCommand cmd = exchange.getIn().getBody(ProcessXmlSigningCommand.class);
                                log.info(">>> [STEP3] VALIDATOR PASSED, calling handleProcessCommand for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentNumber());
                                sagaCommandPort.handleProcessCommand(cmd);
                                log.info(">>> [STEP4] handleProcessCommand RETURNED for saga: {}", cmd.getSagaId());
                        })
                        .log(">>> Successfully processed saga command")
                        // Catch any exception thrown anywhere in the route and log it before DLQ
                        .onException(Exception.class)
                            .process(exchange -> {
                                Exception cause = exchange.getException();
                                // Log the exchange properties and headers for debugging
                                log.error(">>> [ONEXCEPTION] Exchange properties: {}", exchange.getProperties());
                                log.error(">>> [ONEXCEPTION] Exchange in headers: {}", exchange.getIn().getHeaders());
                                log.error(">>> [ONEXCEPTION] Exchange in body type: {}", exchange.getIn().getBody() != null ? exchange.getIn().getBody().getClass().getName() : "null");
                                if (cause == null) {
                                    // Check if there's a cause stored differently
                                    Object causeObj = exchange.getProperty("CamelException");
                                    if (causeObj instanceof Exception) {
                                        cause = (Exception) causeObj;
                                        log.error(">>> [ONEXCEPTION] Found exception from CamelException property: {}: {}",
                                                cause.getClass().getSimpleName(), cause.getMessage());
                                    } else {
                                        cause = new RuntimeException("Unknown error in saga-command-consumer (exception was null)");
                                    }
                                }
                                log.error(">>> [ONEXCEPTION] EXCEPTION type={} message={}",
                                        cause.getClass().getSimpleName(), cause.getMessage());
                                log.error(">>> [ONEXCEPTION] Full stack trace:", cause);
                                if (cause instanceof jakarta.validation.ConstraintViolationException cve) {
                                    log.error(">>> [ONEXCEPTION] Validation errors: {}",
                                            cve.getConstraintViolations().stream()
                                            .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                                            .collect(Collectors.joining("; ")));
                                }
                                if (cause.getCause() != null) {
                                    log.error(">>> [ONEXCEPTION] Caused by: {}: {}",
                                            cause.getCause().getClass().getSimpleName(),
                                            cause.getCause().getMessage());
                                }
                            })
                            .handled(true)
                            .stop();

        // ============================================================
        // CONSUMER ROUTE: saga.compensation.xml-signing (from orchestrator)
        // ============================================================
        from(kafkaConsumerUrl(sagaCompensationTopic))
                        .routeId("saga-compensation-consumer")
                        .log("Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, CompensateXmlSigningCommand.class)
                        .process(commandValidator)  // Validate command before processing
                        .process(exchange -> {
                                CompensateXmlSigningCommand cmd = exchange.getIn().getBody(CompensateXmlSigningCommand.class);
                                log.info("Processing compensation for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandPort.handleCompensation(cmd);
                        })
                        .log("Successfully processed compensation command");
    }
}
