package com.smartcheckout.risk.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration
class KafkaConfig {

    static final String CHECKOUT_EVENTS_DLT = Topics.CHECKOUT_EVENTS + ".DLT";

    @Bean
    NewTopic riskEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                             @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.RISK_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    /** Also declared here so the service can start before checkout-service (same settings, no conflict). */
    @Bean
    NewTopic checkoutEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                 @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.CHECKOUT_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic checkoutEventsDeadLetterTopic(@Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(CHECKOUT_EVENTS_DLT).partitions(1).replicas(replicas).build();
    }

    /**
     * Transient failures (MongoDB or Kafka unavailable) are retried 3 times, one second apart.
     * A message that can never be processed (invalid JSON) goes straight to the dead-letter topic,
     * so it does not block the partition: it can be inspected and replayed later.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumerRecord, exception) -> new TopicPartition(consumerRecord.topic() + ".DLT", -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
        errorHandler.addNotRetryableExceptions(JacksonException.class);
        return errorHandler;
    }
}
