package com.smartcheckout.risk.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.risk.RiskAssessed;
import com.smartcheckout.contracts.risk.RiskEvent;
import com.smartcheckout.risk.application.StoredAssessment;
import com.smartcheckout.risk.application.port.RiskEventPublisher;
import com.smartcheckout.risk.domain.RiskAssessment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes RiskAssessed synchronously: the listener only acknowledges the incoming message
 * once Kafka has confirmed the outgoing one.
 */
@Component
class KafkaRiskEventPublisher implements RiskEventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper jsonMapper;
    private final long sendTimeoutMs;

    KafkaRiskEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper jsonMapper,
                            @Value("${smartcheckout.kafka.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(StoredAssessment stored) {
        RiskAssessment assessment = stored.assessment();
        RiskEvent event = new RiskAssessed(
                stored.riskEventId(),
                UUID.fromString(assessment.checkoutId()),
                assessment.assessedAt(),
                assessment.decision().name(),
                assessment.score(),
                assessment.reasons().stream()
                        .map(reason -> new RiskAssessed.Reason(reason.code(), reason.points(), reason.detail()))
                        .toList());
        String payload = jsonMapper.writerFor(RiskEvent.class).writeValueAsString(event);
        try {
            kafka.send(Topics.RISK_EVENTS, assessment.checkoutId(), payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing RiskAssessed", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("RiskAssessed not published for checkout " + assessment.checkoutId(), e);
        }
    }
}
