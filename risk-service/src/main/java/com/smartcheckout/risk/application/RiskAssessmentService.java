package com.smartcheckout.risk.application;

import com.smartcheckout.risk.application.port.AssessmentStore;
import com.smartcheckout.risk.application.port.RiskEventPublisher;
import com.smartcheckout.risk.domain.RiskAssessment;
import com.smartcheckout.risk.domain.RiskContext;
import com.smartcheckout.risk.domain.RiskEngine;
import com.smartcheckout.risk.domain.TransactionFacts;
import com.smartcheckout.risk.domain.rules.CardVelocityRule;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Scores a checkout once, stores the result, then publishes it.
 * <p>
 * Idempotent consumer: Kafka delivers at least once, so the same CheckoutRequested can arrive twice.
 * The second time, nothing is recomputed: the stored decision is republished with the same event id.
 * This also covers a crash between "stored" and "published".
 */
@Service
public class RiskAssessmentService {

    private final AssessmentStore store;
    private final RiskEventPublisher publisher;
    private final RiskEngine engine;
    private final Clock clock;

    public RiskAssessmentService(AssessmentStore store, RiskEventPublisher publisher, RiskEngine engine, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.engine = engine;
        this.clock = clock;
    }

    public StoredAssessment assess(UUID sourceEventId, TransactionFacts facts) {
        Optional<StoredAssessment> existing = store.findByCheckoutId(facts.checkoutId());
        if (existing.isPresent()) {
            publisher.publish(existing.get());
            return existing.get();
        }
        RiskAssessment assessment = engine.assess(facts, contextOf(facts), clock.instant());
        StoredAssessment stored = new StoredAssessment(UUID.randomUUID(), sourceEventId, facts, assessment);
        store.save(stored);
        publisher.publish(stored);
        return stored;
    }

    public Optional<StoredAssessment> find(String checkoutId) {
        return store.findByCheckoutId(checkoutId);
    }

    /** History measured in event time (occurredAt), so replaying old events gives the same result. */
    private RiskContext contextOf(TransactionFacts facts) {
        int recentPaymentsOnCard = facts.cardToken() == null ? 0
                : store.countRecentPaymentsOnCard(facts.cardToken(),
                        facts.occurredAt().minus(CardVelocityRule.WINDOW), facts.occurredAt(), facts.checkoutId());
        boolean knownCustomer = facts.customerId() != null
                && store.customerHasHistory(facts.customerId(), facts.checkoutId());
        boolean knownDevice = knownCustomer && facts.deviceId() != null
                && store.customerUsedDevice(facts.customerId(), facts.deviceId(), facts.checkoutId());
        return new RiskContext(recentPaymentsOnCard, knownCustomer, knownDevice);
    }
}
