package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.application.dto.event.DocumentArchiveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDocumentArchiveAdapterTest {

    private final OutboxService outbox = mock(OutboxService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxDocumentArchiveAdapter adapter = new OutboxDocumentArchiveAdapter(outbox, mapper);

    @Test
    void publishesToDocumentArchiveTopicPartitionedByDocumentId() {
        DocumentArchiveEvent event = new DocumentArchiveEvent(
                "doc-123", "INV-001", "TAX_INVOICE", "SIGNED_XML",
                "http://minio/bucket/signed.xml", "signed.xml", "application/xml", 1234L,
                "saga-1", "corr-1");

        when(outbox.saveWithRouting(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(OutboxEvent.class));

        adapter.publish(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> partitionKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headers = ArgumentCaptor.forClass(String.class);

        verify(outbox).saveWithRouting(
                eq(event), aggregateType.capture(), aggregateId.capture(),
                topic.capture(), partitionKey.capture(), headers.capture());

        assertThat(topic.getValue()).isEqualTo("document.archive");
        assertThat(partitionKey.getValue()).isEqualTo("doc-123");
        assertThat(aggregateType.getValue()).isEqualTo("SignedXmlDocument");
        assertThat(aggregateId.getValue()).isEqualTo("doc-123");
        assertThat(headers.getValue()).contains("\"correlationId\":\"corr-1\"");
        assertThat(headers.getValue()).contains("\"artifactType\":\"SIGNED_XML\"");
    }
}
