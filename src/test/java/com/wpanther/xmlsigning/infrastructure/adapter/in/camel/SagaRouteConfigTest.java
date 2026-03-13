package com.wpanther.xmlsigning.infrastructure.adapter.in.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.application.usecase.SagaCommandPort;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
@UseAdviceWith
class SagaRouteConfigTest {

    @Autowired
    private CamelContext camelContext;

    @MockBean
    private SagaCommandPort sagaCommandPort;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Replace Kafka endpoints with direct endpoints for testing
        AdviceWith.adviceWith(camelContext, "saga-command-consumer", a -> {
            a.replaceFromWith("direct:saga-command");
        });
        AdviceWith.adviceWith(camelContext, "saga-compensation-consumer", a -> {
            a.replaceFromWith("direct:saga-compensation");
        });

        camelContext.start();
    }

    @Test
    void shouldHaveAllRoutesConfigured() {
        List<Route> routes = camelContext.getRoutes();
        assertFalse(routes.isEmpty(), "No Camel routes found");

        List<String> routeIds = routes.stream()
            .map(Route::getRouteId)
            .toList();

        assertTrue(routeIds.contains("saga-command-consumer"),
            "Missing saga-command-consumer route. Found: " + routeIds);
        assertTrue(routeIds.contains("saga-compensation-consumer"),
            "Missing saga-compensation-consumer route. Found: " + routeIds);
    }

    @Test
    void shouldHaveExactlyTwoRoutes() {
        List<Route> routes = camelContext.getRoutes();
        assertEquals(2, routes.size(),
            "Expected exactly 2 routes but found: " + routes.stream()
                .map(Route::getRouteId).toList());
    }

    @Test
    void shouldProcessSagaCommand() throws Exception {
        ProcessXmlSigningCommand command = new ProcessXmlSigningCommand(
            "saga-1", com.wpanther.saga.domain.enums.SagaStep.SIGN_XML, "corr-1",
            "doc-1",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><CrossIndustryInvoice xsi:schemaLocation=\"urn:un:unece:unce:data:standard:CrossIndustryInvoice:100\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"></CrossIndustryInvoice>",
            "INV-001", "INVOICE"
        );
        String json = objectMapper.writeValueAsString(command);

        ProducerTemplate producer = camelContext.createProducerTemplate();
        producer.sendBody("direct:saga-command", json);

        verify(sagaCommandPort).handleProcessCommand(any(ProcessXmlSigningCommand.class));
    }

    @Test
    void shouldProcessCompensationCommand() throws Exception {
        CompensateXmlSigningCommand command = new CompensateXmlSigningCommand(
            "saga-1", com.wpanther.saga.domain.enums.SagaStep.SIGN_XML, "corr-1",
            "sign-xml", "doc-1", "INVOICE"
        );
        String json = objectMapper.writeValueAsString(command);

        ProducerTemplate producer = camelContext.createProducerTemplate();
        producer.sendBody("direct:saga-compensation", json);

        verify(sagaCommandPort).handleCompensation(any(CompensateXmlSigningCommand.class));
    }
}
