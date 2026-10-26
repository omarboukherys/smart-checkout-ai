package com.smartcheckout.agent.application;

import com.smartcheckout.agent.application.port.InvestigationStore;
import com.smartcheckout.agent.application.port.Investigator;
import com.smartcheckout.agent.domain.Investigation;
import com.smartcheckout.agent.domain.InvestigationReport;
import com.smartcheckout.agent.domain.Recommendation;
import com.smartcheckout.agent.domain.ToolCall;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T17:00:00Z");
    private static final InvestigationRequest REQUEST =
            new InvestigationRequest("chk-1", 60, List.of("HIGH_AMOUNT", "GEO_MISMATCH"));

    private final InMemoryStore store = new InMemoryStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void stores_the_agent_report_with_the_model_and_the_tools_it_used() {
        ScriptedAgent agent = new ScriptedAgent(request -> new Investigator.Result(
                new InvestigationReport(Recommendation.ESCALATE, 0.7, "Carte FR, IP MA, client inconnu.",
                        List.of("Score 60"), List.of("Êtes-vous à l'étranger ?")),
                List.of(new ToolCall("getRiskAssessment", "chk-1", 12, true),
                        new ToolCall("getCheckout", "chk-1", 9, true))));

        Investigation investigation = service(agent, 10).investigate(REQUEST, false);

        assertThat(investigation.generatedBy()).isEqualTo("test-model");
        assertThat(investigation.fallbackReason()).isNull();
        assertThat(investigation.toolCalls()).extracting(ToolCall::tool).containsExactly("getRiskAssessment", "getCheckout");
        assertThat(store.find("chk-1")).isEqualTo(investigation);
    }

    @Test
    void falls_back_to_the_rules_when_the_agent_fails() {
        ScriptedAgent agent = new ScriptedAgent(request -> {
            throw new IllegalStateException("429 Too Many Requests");
        });

        Investigation investigation = service(agent, 10).investigate(REQUEST, false);

        assertThat(investigation.generatedBy()).isEqualTo(Investigation.RULES);
        assertThat(investigation.fallbackReason()).startsWith("AI error");
        assertThat(investigation.report().recommendation()).isEqualTo(Recommendation.ESCALATE);
    }

    @Test
    void falls_back_to_the_rules_when_the_answer_is_invalid() {
        ScriptedAgent agent = new ScriptedAgent(request -> new Investigator.Result(
                new InvestigationReport(Recommendation.APPROVE, 1.4, "x", null, null), List.of()));

        Investigation investigation = service(agent, 10).investigate(REQUEST, false);

        assertThat(investigation.byAi()).isFalse();
        assertThat(investigation.fallbackReason()).startsWith("Invalid AI answer");
    }

    @Test
    void uses_the_rules_when_no_api_key_is_configured() {
        InvestigationService service = new InvestigationService(Optional.empty(), store, new DailyBudget(10), clock);

        Investigation investigation = service.investigate(REQUEST, false);

        assertThat(investigation.byAi()).isFalse();
        assertThat(investigation.fallbackReason()).contains("no API key");
    }

    @Test
    void uses_the_rules_once_the_daily_budget_is_spent() {
        ScriptedAgent agent = new ScriptedAgent(request -> new Investigator.Result(
                new InvestigationReport(Recommendation.ESCALATE, 0.7, "ok", null, null), List.of()));
        InvestigationService service = service(agent, 1);

        service.investigate(new InvestigationRequest("chk-1", 60, List.of()), false);
        Investigation second = service.investigate(new InvestigationRequest("chk-2", 60, List.of()), false);

        assertThat(agent.calls).isEqualTo(1);
        assertThat(second.fallbackReason()).startsWith("Daily AI budget reached");
    }

    @Test
    void an_existing_investigation_is_reused_unless_a_new_one_is_forced() {
        ScriptedAgent agent = new ScriptedAgent(request -> new Investigator.Result(
                new InvestigationReport(Recommendation.ESCALATE, 0.7, "ok", null, null), List.of()));
        InvestigationService service = service(agent, 10);

        service.investigate(REQUEST, false);
        service.investigate(REQUEST, false);
        assertThat(agent.calls).isEqualTo(1);

        service.investigate(REQUEST, true);
        assertThat(agent.calls).isEqualTo(2);
    }

    private InvestigationService service(Investigator agent, int dailyBudget) {
        return new InvestigationService(Optional.of(agent), store, new DailyBudget(dailyBudget), clock);
    }

    private static final class ScriptedAgent implements Investigator {

        private final Function<InvestigationRequest, Result> behaviour;
        private int calls;

        ScriptedAgent(Function<InvestigationRequest, Result> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public Result investigate(InvestigationRequest request) {
            calls++;
            return behaviour.apply(request);
        }

        @Override
        public String model() {
            return "test-model";
        }
    }

    private static final class InMemoryStore implements InvestigationStore {

        private final Map<String, Investigation> investigations = new HashMap<>();

        Investigation find(String checkoutId) {
            return investigations.get(checkoutId);
        }

        @Override
        public Optional<Investigation> findByCheckoutId(String checkoutId) {
            return Optional.ofNullable(investigations.get(checkoutId));
        }

        @Override
        public void save(Investigation investigation) {
            investigations.put(investigation.checkoutId(), investigation);
        }

        @Override
        public List<Investigation> findRecent(int limit) {
            return new ArrayList<>(investigations.values()).stream().limit(limit).toList();
        }
    }
}
