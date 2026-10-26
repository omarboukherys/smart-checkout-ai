package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service produces to; Spring Kafka creates them at startup if missing.
 */
@Configuration
class KafkaTopicsConfig {

    @Bean
    NewTopic checkoutEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                 @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.CHECKOUT_EVENTS)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
