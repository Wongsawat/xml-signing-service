package com.wpanther.xmlsigning.integration.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;

/**
 * Kafka consumer configuration for CDC integration tests.
 * Provides a raw KafkaConsumer to poll type-specific xml.signed.* topics
 * that Debezium CDC publishes to.
 */
@Configuration
public class TestKafkaConsumerConfig {

    @Value("${app.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @Bean
    public Properties kafkaAdminProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        return props;
    }

    @Bean
    public KafkaConsumer<String, String> testKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "cdc-test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    /**
     * Create the type-specific output topics that Debezium CDC routes to.
     */
    public void createTopics() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdminProperties())) {
            List<NewTopic> topics = List.of(
                new NewTopic("xml.signed.tax-invoice", 1, (short) 1),
                new NewTopic("xml.signed.invoice", 1, (short) 1),
                new NewTopic("xml.signed.receipt", 1, (short) 1),
                new NewTopic("xml.signed.debit-credit-note", 1, (short) 1),
                new NewTopic("xml.signed.cancellation", 1, (short) 1),
                new NewTopic("xml.signed.abbreviated", 1, (short) 1)
            );
            adminClient.createTopics(topics).all().get();
        } catch (Exception e) {
            // Topics may already exist from previous runs
        }
    }
}
