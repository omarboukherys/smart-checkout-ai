package com.smartcheckout.checkout.infrastructure.messaging;

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

/**
 * Topics this service produces to (created at startup if missing), dead-letter topics for the topics
 * it consumes, and the error handling of its listeners.
 */
@Configuration
class KafkaTopicsConfig {

    @Bean
    NewTopic checkoutEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                 @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.CHECKOUT_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentCommandsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                  @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic riskEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                             @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.RISK_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic riskEventsDeadLetterTopic(@Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.RISK_EVENTS + ".DLT").partitions(1).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentEventsDeadLetterTopic(@Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS + ".DLT").partitions(1).replicas(replicas).build();
    }

    /** Transient failures retried 3 times, unreadable messages straight to the dead-letter topic. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumerRecord, exception) -> new TopicPartition(consumerRecord.topic() + ".DLT", -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
        errorHandler.addNotRetryableExceptions(JacksonException.class);
        return errorHandler;
    }
}
