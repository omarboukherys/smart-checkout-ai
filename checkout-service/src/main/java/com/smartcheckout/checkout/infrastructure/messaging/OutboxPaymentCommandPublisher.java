package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.checkout.application.port.PaymentCommandPublisher;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.payment.AuthorizePayment;
import com.smartcheckout.contracts.payment.PaymentCommand;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes the AuthorizePayment command to the same outbox as the events; the relay sends it to
 * {@code payment.commands}, keyed by checkout id.
 */
@Component
class OutboxPaymentCommandPublisher implements PaymentCommandPublisher {

    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    OutboxPaymentCommandPublisher(EntityManager entityManager, JsonMapper jsonMapper, Clock clock) {
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void requestAuthorization(Checkout checkout) {
        Instant now = clock.instant();
        PaymentCommand command = new AuthorizePayment(
                UUID.randomUUID(),
                checkout.id(),
                now,
                checkout.totalMinor(),
                checkout.currency(),
                checkout.payment().method().name(),
                checkout.payment().token());
        String payload = jsonMapper.writerFor(PaymentCommand.class).writeValueAsString(command);
        entityManager.persist(OutboxEventEntity.pending(command.commandId(), checkout.id().toString(),
                Topics.PAYMENT_COMMANDS, command.getClass().getSimpleName(), payload, now));
    }
}
