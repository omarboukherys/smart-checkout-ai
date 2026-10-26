package com.smartcheckout.checkout.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox and publishes pending events to Kafka, keyed by checkout id.
 * <p>
 * Delivery is at-least-once: if the service crashes after sending but before committing,
 * the event is sent again. Consumers de-duplicate with {@code eventId}.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int batchSize;
    private final long sendTimeoutMs;

    OutboxRelay(OutboxEventJpaRepository outbox,
                KafkaTemplate<String, String> kafka,
                TransactionTemplate transaction,
                Clock clock,
                @Value("${smartcheckout.outbox.batch-size:100}") int batchSize,
                @Value("${smartcheckout.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transaction = transaction;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${smartcheckout.outbox.poll-interval-ms:500}")
    public void publishPendingEvents() {
        transaction.executeWithoutResult(status -> {
            for (OutboxEventEntity event : outbox.lockNextBatch(batchSize)) {
                if (!send(event)) {
                    // Stop at the first failure so that events keep their order; retried on the next run.
                    break;
                }
            }
        });
    }

    private boolean send(OutboxEventEntity event) {
        try {
            kafka.send(event.topic(), event.aggregateId(), event.payload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished(clock.instant());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("Interrupted while publishing");
            return false;
        } catch (ExecutionException | TimeoutException e) {
            event.markFailed(e.getMessage());
            log.warn("Outbox event {} not published yet (attempt {}): {}", event.id(), event.attempts(), e.getMessage());
            return false;
        }
    }
}
