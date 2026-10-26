package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.checkout.application.CheckoutOrchestrator;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.payment.PaymentAuthorized;
import com.smartcheckout.contracts.payment.PaymentDeclined;
import com.smartcheckout.contracts.payment.PaymentEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class PaymentEventsListener {

    private final CheckoutOrchestrator orchestrator;
    private final JsonMapper jsonMapper;

    PaymentEventsListener(CheckoutOrchestrator orchestrator, JsonMapper jsonMapper) {
        this.orchestrator = orchestrator;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onPaymentEvent(String payload) {
        PaymentEvent event = jsonMapper.readValue(payload, PaymentEvent.class);
        switch (event) {
            case PaymentAuthorized authorized ->
                    orchestrator.onPaymentAuthorized(authorized.eventId(), authorized.checkoutId(),
                            authorized.pspReference());
            case PaymentDeclined declined ->
                    orchestrator.onPaymentDeclined(declined.eventId(), declined.checkoutId(),
                            declined.reasonCode(), declined.reasonMessage());
        }
    }
}
