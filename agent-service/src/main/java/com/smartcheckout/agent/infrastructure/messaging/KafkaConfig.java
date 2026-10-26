package com.smartcheckout.agent.infrastructure.messaging;

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

    /** Same settings as risk-service and checkout-service, so the service can start first. */
    @Bean
    NewTopic riskEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                             @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.RISK_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    /** Shared with checkout-service; the failing consumer group is in the kafka_dlt-original-consumer-group header. */
    @Bean
    NewTopic riskEventsDeadLetterTopic(@Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.RISK_EVENTS + ".DLT").partitions(1).replicas(replicas).build();
    }

    /**
     * The AI never fails the message (it falls back to the rules), so a retry here means MongoDB is down.
     * Retried 3 times, then sent to the dead-letter topic; invalid JSON goes there directly.
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
