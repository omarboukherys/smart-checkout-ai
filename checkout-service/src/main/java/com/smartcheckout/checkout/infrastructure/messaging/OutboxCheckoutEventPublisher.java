package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.checkout.application.port.CheckoutEventPublisher;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.checkout.CheckoutEvent;
import com.smartcheckout.contracts.checkout.CheckoutRequested;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Implements the publisher port by writing to the outbox table, inside the caller's transaction.
 * Nothing is sent to Kafka here: {@link OutboxRelay} does it after the commit.
 */
@Component
class OutboxCheckoutEventPublisher implements CheckoutEventPublisher {

    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    OutboxCheckoutEventPublisher(EntityManager entityManager, JsonMapper jsonMapper) {
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void checkoutRequested(Checkout checkout) {
        append(new CheckoutRequested(
                UUID.randomUUID(),
                checkout.id(),
                checkout.createdAt(),
                checkout.storeId(),
                checkout.channel().name(),
                checkout.customerId(),
                checkout.totalMinor(),
                checkout.currency(),
                checkout.payment().method().name(),
                checkout.payment().token(),
                checkout.payment().last4(),
                checkout.payment().country(),
                checkout.context().ipCountry(),
                checkout.context().deviceId(),
                checkout.itemCount()));
    }

    private void append(CheckoutEvent event) {
        String payload = jsonMapper.writerFor(CheckoutEvent.class).writeValueAsString(event);
        entityManager.persist(OutboxEventEntity.pending(
                event.eventId(),
                event.checkoutId().toString(),
                Topics.CHECKOUT_EVENTS,
                event.getClass().getSimpleName(),
                payload,
                event.occurredAt()));
    }
}
