package com.wpanther.xmlsigning.infrastructure.config;

import com.wpanther.xmlsigning.application.service.XmlSigningOrchestrationService;
import com.wpanther.xmlsigning.domain.event.XmlSigningRequestedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for XML signing.
 * Replaces Spring Kafka consumer and producer configuration.
 *
 * Routes signed XML to type-specific Kafka topics based on document type:
 * - xml.signed.tax-invoice (TAX_INVOICE)
 * - xml.signed.receipt (RECEIPT)
 * - xml.signed.invoice (INVOICE)
 * - xml.signed.debit-credit-note (DEBIT_CREDIT_NOTE)
 * - xml.signed.cancellation (CANCELLATION_NOTE)
 * - xml.signed.abbreviated (ABBREVIATED_TAX_INVOICE)
 */
@Component
@Slf4j
public class XmlSigningRouteConfig extends RouteBuilder {

    private final XmlSigningOrchestrationService orchestrationService;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.xml-signing-requested}")
    private String inputTopic;

    // Type-specific output topics
    @Value("${app.kafka.topics.xml-signed-tax-invoice}")
    private String taxInvoiceTopic;

    @Value("${app.kafka.topics.xml-signed-receipt}")
    private String receiptTopic;

    @Value("${app.kafka.topics.xml-signed-invoice}")
    private String invoiceTopic;

    @Value("${app.kafka.topics.xml-signed-debit-credit-note}")
    private String debitCreditNoteTopic;

    @Value("${app.kafka.topics.xml-signed-cancellation}")
    private String cancellationTopic;

    @Value("${app.kafka.topics.xml-signed-abbreviated}")
    private String abbreviatedTopic;

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

                orchestrationService.processSigningRequest(event);
            })

            .log("Successfully processed XML signing request");

        // ============================================================
        // PRODUCER ROUTE: Type-specific routing for signed XML
        // ============================================================
        from("direct:publish-xml-signed")
            .routeId("xml-signed-producer")
            .log("Publishing XmlSignedEvent: ${body.invoiceNumber}, documentType: ${header.documentType}")
            .marshal().json(JsonLibrary.Jackson)
            .choice()
                .when(header("documentType").isEqualTo(DocumentType.TAX_INVOICE.name()))
                    .to("kafka:" + taxInvoiceTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published TaxInvoice to: " + taxInvoiceTopic)
                .when(header("documentType").isEqualTo(DocumentType.RECEIPT.name()))
                    .to("kafka:" + receiptTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published Receipt to: " + receiptTopic)
                .when(header("documentType").isEqualTo(DocumentType.INVOICE.name()))
                    .to("kafka:" + invoiceTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published Invoice to: " + invoiceTopic)
                .when(header("documentType").isEqualTo(DocumentType.DEBIT_CREDIT_NOTE.name()))
                    .to("kafka:" + debitCreditNoteTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published DebitCreditNote to: " + debitCreditNoteTopic)
                .when(header("documentType").isEqualTo(DocumentType.CANCELLATION_NOTE.name()))
                    .to("kafka:" + cancellationTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published CancellationNote to: " + cancellationTopic)
                .when(header("documentType").isEqualTo(DocumentType.ABBREVIATED_TAX_INVOICE.name()))
                    .to("kafka:" + abbreviatedTopic + "?brokers=" + kafkaBrokers + "&key=${header.kafka.KEY}")
                    .log("Published AbbreviatedTaxInvoice to: " + abbreviatedTopic)
                .otherwise()
                    .log("Unknown document type: ${header.documentType}, sending to DLQ")
                    .to("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .end();
    }
}
