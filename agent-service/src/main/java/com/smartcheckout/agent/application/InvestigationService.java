package com.smartcheckout.agent.application;

import com.smartcheckout.agent.application.port.InvestigationStore;
import com.smartcheckout.agent.application.port.Investigator;
import com.smartcheckout.agent.application.port.RiskDecisionLookup;
import com.smartcheckout.agent.domain.DomainException;
import com.smartcheckout.agent.domain.FallbackAdvisor;
import com.smartcheckout.agent.domain.Investigation;
import com.smartcheckout.agent.domain.InvestigationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Runs an investigation and always produces a report for the supervisor:
 * the AI agent when it is available and within budget, the rules otherwise.
 * The agent only recommends: nothing here can approve or reject a payment.
 */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final Optional<Investigator> investigator;
    private final InvestigationStore store;
    private final RiskDecisionLookup riskDecisions;
    private final DailyBudget budget;
    private final FallbackAdvisor fallback = new FallbackAdvisor();
    private final Clock clock;

    public InvestigationService(Optional<Investigator> investigator, InvestigationStore store,
                                RiskDecisionLookup riskDecisions, DailyBudget budget, Clock clock) {
        this.investigator = investigator;
        this.store = store;
        this.riskDecisions = riskDecisions;
        this.budget = budget;
        this.clock = clock;
    }

    /**
     * @param force true to run again even if the checkout was already investigated (supervisor's request)
     */
    public Investigation investigate(InvestigationRequest request, boolean force) {
        if (!force) {
            Optional<Investigation> existing = store.findByCheckoutId(request.checkoutId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Instant startedAt = clock.instant();
        Investigation investigation = investigator
                .map(agent -> withAgent(agent, request, startedAt))
                .orElseGet(() -> withRules(request, startedAt, "AI disabled: no API key configured"));
        store.save(investigation);
        log.info("Checkout {} investigated by {}: {} ({} ms)", request.checkoutId(), investigation.generatedBy(),
                investigation.report().recommendation(), investigation.durationMs());
        return investigation;
    }

    public Optional<Investigation> find(String checkoutId) {
        return store.findByCheckoutId(checkoutId);
    }

    /**
     * Runs a new investigation at the supervisor's request, from the current risk decision.
     * Empty if the risk-service does not know the checkout.
     */
    public Optional<Investigation> reinvestigate(String checkoutId) {
        return riskDecisions.find(checkoutId).map(request -> investigate(request, true));
    }

    public List<Investigation> recent(int limit) {
        return store.findRecent(limit);
    }

    private Investigation withAgent(Investigator agent, InvestigationRequest request, Instant startedAt) {
        if (!budget.tryConsume(LocalDate.now(clock))) {
            return withRules(request, startedAt, "Daily AI budget reached (" + budget.maxPerDay() + ")");
        }
        try {
            Investigator.Result result = agent.investigate(request);
            return new Investigation(request.checkoutId(), request.riskScore(), request.reasonCodes(),
                    result.report(), result.model(), null, result.toolCalls(), startedAt, clock.instant());
        } catch (DomainException e) {
            log.warn("Invalid AI answer for checkout {}: {}", request.checkoutId(), e.getMessage());
            return withRules(request, startedAt, "Invalid AI answer: " + e.getMessage());
        } catch (RuntimeException e) {
            // Libraries wrap the real error (HTTP 404, 429...): the root cause is what helps.
            Throwable rootCause = rootCause(e);
            log.warn("AI investigation failed for checkout {}: {}", request.checkoutId(), rootCause.toString(), e);
            return withRules(request, startedAt, "AI error: " + rootCause.getClass().getSimpleName());
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private Investigation withRules(InvestigationRequest request, Instant startedAt, String reason) {
        InvestigationReport report = fallback.advise(request.riskScore(), request.reasonCodes());
        return new Investigation(request.checkoutId(), request.riskScore(), request.reasonCodes(), report,
                Investigation.RULES, reason, List.of(), startedAt, clock.instant());
    }
}
