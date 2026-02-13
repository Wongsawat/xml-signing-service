package com.wpanther.xmlsigning.infrastructure.config;

import com.wpanther.xmlsigning.application.service.SagaCommandHandler;
import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for saga command and compensation consumers.
 * Saga-only mode: replaces old xml.signing.requested topic consumption.
 */
@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-xml-signing}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-xml-signing}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:xml.signing.dlq}")
    private String dlqTopic;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler - Dead Letter Channel with retries
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
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
        from("kafka:" + sagaCommandTopic
                        + "?brokers=" + kafkaBrokers
                        + "&groupId=xml-signing-service"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError=true"
                        + "&maxPollRecords=100"
                        + "&consumersCount=3")
                        .routeId("saga-command-consumer")
                        .log("Received saga command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, ProcessXmlSigningCommand.class)
                        .process(exchange -> {
                                ProcessXmlSigningCommand cmd = exchange.getIn().getBody(ProcessXmlSigningCommand.class);
                                log.info("Processing saga command for saga: {}, invoice: {}",
                                                cmd.getSagaId(), cmd.getInvoiceNumber());
                                sagaCommandHandler.handleProcessCommand(cmd);
                        })
                        .log("Successfully processed saga command");

        // ============================================================
        // CONSUMER ROUTE: saga.compensation.xml-signing (from orchestrator)
        // ============================================================
        from("kafka:" + sagaCompensationTopic
                        + "?brokers=" + kafkaBrokers
                        + "&groupId=xml-signing-service"
                        + "&autoOffsetReset=earliest"
                        + "&autoCommitEnable=false"
                        + "&breakOnFirstError=true"
                        + "&maxPollRecords=100"
                        + "&consumersCount=3")
                        .routeId("saga-compensation-consumer")
                        .log("Received compensation command from Kafka: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                        .unmarshal().json(JsonLibrary.Jackson, CompensateXmlSigningCommand.class)
                        .process(exchange -> {
                                CompensateXmlSigningCommand cmd = exchange.getIn().getBody(CompensateXmlSigningCommand.class);
                                log.info("Processing compensation for saga: {}, document: {}",
                                                cmd.getSagaId(), cmd.getDocumentId());
                                sagaCommandHandler.handleCompensation(cmd);
                        })
                        .log("Successfully processed compensation command");
    }
}
