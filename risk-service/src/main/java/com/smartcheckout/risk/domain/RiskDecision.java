package com.smartcheckout.risk.domain;

public enum RiskDecision {
    /** Low risk: the payment goes straight to the payment provider. */
    APPROVE,
    /** Medium risk: the payment is held for a human review, assisted by the AI agent. */
    REVIEW,
    /** High risk: the payment is refused. */
    BLOCK;

    public static final int REVIEW_THRESHOLD = 40;
    public static final int BLOCK_THRESHOLD = 70;

    public static RiskDecision fromScore(int score) {
        if (score >= BLOCK_THRESHOLD) {
            return BLOCK;
        }
        if (score >= REVIEW_THRESHOLD) {
            return REVIEW;
        }
        return APPROVE;
    }
}
