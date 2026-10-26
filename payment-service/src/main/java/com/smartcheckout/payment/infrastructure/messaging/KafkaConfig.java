package com.smartcheckout.payment.infrastructure.messaging;

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
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;

@Configuration
class KafkaConfig {

    @Bean
    NewTopic paymentCommandsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                  @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentEventsTopic(@Value("${smartcheckout.kafka.partitions:6}") int partitions,
                                @Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic paymentCommandsDeadLetterTopic(@Value("${smartcheckout.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS + ".DLT").partitions(1).replicas(replicas).build();
    }

    /**
     * Provider outages are retried with an exponential backoff (1 s, 2 s, 4 s); after that the command
     * goes to the dead-letter topic and the payment stays AUTHORIZING, visible for an operator.
     * An unreadable command is not retried at all.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumerRecord, exception) -> new TopicPartition(consumerRecord.topic() + ".DLT", -1));
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(JacksonException.class);
        return errorHandler;
    }
}
