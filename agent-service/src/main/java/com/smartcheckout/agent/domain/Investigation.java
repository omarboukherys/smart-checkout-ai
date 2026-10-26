package com.smartcheckout.agent.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The full record of one investigation: the report shown to the supervisor plus everything needed
 * to audit it later (which model or rules produced it, why a fallback was used, which tools were called).
 *
 * @param generatedBy    model name, or {@link #RULES} when the rule-based fallback produced the report
 * @param fallbackReason why the AI was not used (no key, error, budget, invalid answer), null otherwise
 */
public record Investigation(
        String checkoutId,
        int riskScore,
        List<String> riskReasons,
        InvestigationReport report,
        String generatedBy,
        String fallbackReason,
        List<ToolCall> toolCalls,
        Instant startedAt,
        Instant completedAt
) {

    public static final String RULES = "rules";

    public Investigation {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(generatedBy, "generatedBy");
        riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean byAi() {
        return !RULES.equals(generatedBy);
    }

    public long durationMs() {
        return Duration.between(startedAt, completedAt).toMillis();
    }
}
