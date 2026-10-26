package com.smartcheckout.payment.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.payment.PaymentAuthorized;
import com.smartcheckout.contracts.payment.PaymentDeclined;
import com.smartcheckout.contracts.payment.PaymentEvent;
import com.smartcheckout.payment.application.port.PaymentEventPublisher;
import com.smartcheckout.payment.domain.Payment;
import com.smartcheckout.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper jsonMapper;
    private final long sendTimeoutMs;

    KafkaPaymentEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper jsonMapper,
                               @Value("${smartcheckout.kafka.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(Payment payment) {
        UUID checkoutId = UUID.fromString(payment.checkoutId());
        PaymentEvent event = payment.status() == PaymentStatus.AUTHORIZED
                ? new PaymentAuthorized(eventIdOf(payment), checkoutId, payment.updatedAt(), payment.pspReference(),
                payment.amountMinor(), payment.currency())
                : new PaymentDeclined(eventIdOf(payment), checkoutId, payment.updatedAt(), payment.declineCode(),
                payment.declineMessage());
        String payload = jsonMapper.writerFor(PaymentEvent.class).writeValueAsString(event);
        try {
            kafka.send(Topics.PAYMENT_EVENTS, payment.checkoutId(), payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing the payment outcome", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Payment outcome not published for checkout " + payment.checkoutId(), e);
        }
    }

    /**
     * Deterministic id (name-based UUID): republishing the same outcome after a replay gives the same
     * event id, so checkout-service can recognise and ignore the duplicate. No extra column needed.
     */
    static UUID eventIdOf(Payment payment) {
        String name = "payment:" + payment.checkoutId() + ":" + payment.status();
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
