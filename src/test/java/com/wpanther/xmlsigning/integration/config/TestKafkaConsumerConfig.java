package com.wpanther.xmlsigning.integration.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Properties;

/**
 * Kafka consumer configuration for CDC integration tests.
 * Provides a raw KafkaConsumer to poll the xml.signed topic
 * that Debezium CDC publishes to.
 */
@Configuration
@Profile("cdc-test")
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
     * Create the xml.signed topic that Debezium CDC routes to.
     */
    public void createTopics() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdminProperties())) {
            NewTopic topic = new NewTopic("xml.signed", 1, (short) 1);
            adminClient.createTopics(List.of(topic)).all().get();
        } catch (Exception e) {
            // Topic may already exist from previous runs
        }
    }
}
