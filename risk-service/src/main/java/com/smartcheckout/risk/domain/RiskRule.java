package com.smartcheckout.risk.domain;

import java.util.Optional;

/**
 * One fraud signal. Each rule is a small, independent class: adding a signal means adding a class,
 * without touching the engine or the other rules (open/closed principle).
 */
@FunctionalInterface
public interface RiskRule {

    /** Returns a reason with its points when the signal is present, empty otherwise. */
    Optional<RiskReason> evaluate(TransactionFacts facts, RiskContext context);
}
