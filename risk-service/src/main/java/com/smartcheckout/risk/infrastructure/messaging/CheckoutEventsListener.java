package com.smartcheckout.risk.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.checkout.CheckoutEvent;
import com.smartcheckout.contracts.checkout.CheckoutRequested;
import com.smartcheckout.risk.application.RiskAssessmentService;
import com.smartcheckout.risk.application.StoredAssessment;
import com.smartcheckout.risk.domain.TransactionFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes checkout events. The offset is committed only after the method returns,
 * so a failure (MongoDB or Kafka down) means the message is delivered again.
 */
@Component
class CheckoutEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CheckoutEventsListener.class);

    private final RiskAssessmentService riskAssessmentService;
    private final JsonMapper jsonMapper;

    CheckoutEventsListener(RiskAssessmentService riskAssessmentService, JsonMapper jsonMapper) {
        this.riskAssessmentService = riskAssessmentService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.CHECKOUT_EVENTS)
    public void onCheckoutEvent(String payload) {
        CheckoutEvent event = jsonMapper.readValue(payload, CheckoutEvent.class);
        // Sealed interface: the compiler checks that every event type is handled.
        switch (event) {
            case CheckoutRequested requested -> {
                StoredAssessment stored = riskAssessmentService.assess(requested.eventId(), toFacts(requested));
                log.info("Checkout {} scored {} -> {}", requested.checkoutId(),
                        stored.assessment().score(), stored.assessment().decision());
            }
        }
    }

    static TransactionFacts toFacts(CheckoutRequested event) {
        return new TransactionFacts(event.checkoutId().toString(), event.channel(), event.customerId(),
                event.amountMinor(), event.currency(), event.cardToken(), event.cardCountry(), event.ipCountry(),
                event.deviceId(), event.occurredAt());
    }
}
