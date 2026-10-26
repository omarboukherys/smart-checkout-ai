package com.smartcheckout.agent.infrastructure.messaging;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.application.InvestigationService;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.risk.RiskAssessed;
import com.smartcheckout.contracts.risk.RiskEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Investigates every payment the risk engine holds for review (decision REVIEW).
 * Same event as checkout-service, own consumer group: both services receive it independently.
 * A redelivered event reuses the stored investigation, so the model is not called twice.
 */
@Component
class RiskEventsListener {

    static final String REVIEW = "REVIEW";

    private final InvestigationService investigationService;
    private final JsonMapper jsonMapper;

    RiskEventsListener(InvestigationService investigationService, JsonMapper jsonMapper) {
        this.investigationService = investigationService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.RISK_EVENTS)
    public void onRiskEvent(String payload) {
        RiskEvent event = jsonMapper.readValue(payload, RiskEvent.class);
        switch (event) {
            case RiskAssessed assessed when REVIEW.equals(assessed.decision()) ->
                    investigationService.investigate(toRequest(assessed), false);
            case RiskAssessed ignored -> {
                // APPROVE and BLOCK need no human decision, so no investigation.
            }
        }
    }

    static InvestigationRequest toRequest(RiskAssessed assessed) {
        return new InvestigationRequest(assessed.checkoutId().toString(), assessed.score(),
                assessed.reasons() == null ? null : assessed.reasons().stream().map(RiskAssessed.Reason::code).toList());
    }
}
