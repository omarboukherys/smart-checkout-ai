package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.checkout.application.CheckoutOrchestrator;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.risk.RiskAssessed;
import com.smartcheckout.contracts.risk.RiskEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class RiskEventsListener {

    private final CheckoutOrchestrator orchestrator;
    private final JsonMapper jsonMapper;

    RiskEventsListener(CheckoutOrchestrator orchestrator, JsonMapper jsonMapper) {
        this.orchestrator = orchestrator;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.RISK_EVENTS)
    public void onRiskEvent(String payload) {
        RiskEvent event = jsonMapper.readValue(payload, RiskEvent.class);
        switch (event) {
            case RiskAssessed assessed -> orchestrator.onRiskAssessed(assessed.eventId(), assessed.checkoutId(),
                    assessed.decision(), assessed.score(),
                    assessed.reasons().stream().map(RiskAssessed.Reason::code).toList());
        }
    }
}
