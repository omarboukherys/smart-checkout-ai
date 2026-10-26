package com.smartcheckout.agent.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One investigation in MongoDB, the checkout id as document id: one investigation per checkout,
 * replaced when the supervisor asks for a new one. Everything needed for an audit is kept:
 * who produced the report (model or rules), why the AI was not used, and the tools it called.
 */
@Document(collection = "investigations")
public class InvestigationDocument {

    @Id
    private String checkoutId;
    private int riskScore;
    private List<String> riskReasons;
    private String recommendation;
    private double confidence;
    private String summary;
    private List<String> keyEvidence;
    private List<String> questionsForCustomer;
    private String generatedBy;
    private String fallbackReason;
    private List<ToolCallEntry> toolCalls;
    private Instant startedAt;
    @Indexed
    private Instant completedAt;

    public record ToolCallEntry(String tool, String input, long durationMs, boolean succeeded) {
    }

    protected InvestigationDocument() {
        // required by Spring Data
    }

    @SuppressWarnings("java:S107") // a flat document is clearer than nested builders here
    InvestigationDocument(String checkoutId, int riskScore, List<String> riskReasons, String recommendation,
                          double confidence, String summary, List<String> keyEvidence,
                          List<String> questionsForCustomer, String generatedBy, String fallbackReason,
                          List<ToolCallEntry> toolCalls, Instant startedAt, Instant completedAt) {
        this.checkoutId = checkoutId;
        this.riskScore = riskScore;
        this.riskReasons = riskReasons;
        this.recommendation = recommendation;
        this.confidence = confidence;
        this.summary = summary;
        this.keyEvidence = keyEvidence;
        this.questionsForCustomer = questionsForCustomer;
        this.generatedBy = generatedBy;
        this.fallbackReason = fallbackReason;
        this.toolCalls = toolCalls;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    String checkoutId() {
        return checkoutId;
    }

    int riskScore() {
        return riskScore;
    }

    List<String> riskReasons() {
        return riskReasons;
    }

    String recommendation() {
        return recommendation;
    }

    double confidence() {
        return confidence;
    }

    String summary() {
        return summary;
    }

    List<String> keyEvidence() {
        return keyEvidence;
    }

    List<String> questionsForCustomer() {
        return questionsForCustomer;
    }

    String generatedBy() {
        return generatedBy;
    }

    String fallbackReason() {
        return fallbackReason;
    }

    List<ToolCallEntry> toolCalls() {
        return toolCalls;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant completedAt() {
        return completedAt;
    }
}
