package com.smartcheckout.agent.domain;

/**
 * What the agent suggests to the supervisor. It is only a suggestion: the agent cannot act on it.
 */
public enum Recommendation {
    /** The risk signals are explained by legitimate behaviour. */
    APPROVE,
    /** Strong fraud pattern. */
    REJECT,
    /** Not enough to decide: contact the customer or ask for an extra verification first. */
    ESCALATE
}
