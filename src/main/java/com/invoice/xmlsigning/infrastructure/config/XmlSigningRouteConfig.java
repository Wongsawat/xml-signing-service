package com.invoice.xmlsigning.infrastructure.config;

import com.invoice.xmlsigning.application.service.XmlSigningOrchestrationService;
import com.invoice.xmlsigning.domain.event.XmlSigningRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for XML signing.
 * Replaces Spring Kafka consumer and producer configuration.
 */
@Component
@Slf4j
public class XmlSigningRouteConfig extends RouteBuilder {

    private final XmlSigningOrchestrationService orchestrationService;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.xml-signing-requested}")
    private String inputTopic;

    @Value("${app.kafka.topics.xml-signed}")
    private String outputTopic;

    @Value("${app.kafka.topics.dlq:xml.signing.dlq}")
    private String dlqTopic;

    public XmlSigningRouteConfig(XmlSigningOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
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
        // CONSUMER ROUTE: xml.signing.requested
        // ============================================================
        from("kafka:" + inputTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=xml-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
            .routeId("xml-signing-consumer")
            .log("Received XML signing request: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            // Unmarshal JSON to XmlSigningRequestedEvent
            .unmarshal().json(JsonLibrary.Jackson, XmlSigningRequestedEvent.class)

            // Process the event - call application service
            .process(exchange -> {
                XmlSigningRequestedEvent event = exchange.getIn().getBody(XmlSigningRequestedEvent.class);
                log.info("Processing XML signing request for invoice: {}", event.getInvoiceNumber());

                // Call existing application service (unchanged)
                orchestrationService.processSigningRequest(event);
            })

            .log("Successfully processed XML signing request");

        // ============================================================
        // PRODUCER ROUTE: xml.signed
        // ============================================================
        from("direct:publish-xml-signed")
            .routeId("xml-signed-producer")
            .log("Publishing XmlSignedEvent: ${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + outputTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
            .log("Published XmlSignedEvent to " + outputTopic);
    }
}
