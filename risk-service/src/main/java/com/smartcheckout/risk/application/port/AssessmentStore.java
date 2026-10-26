package com.smartcheckout.risk.application.port;

import com.smartcheckout.risk.application.StoredAssessment;

import java.time.Instant;
import java.util.Optional;

/**
 * Output port: past assessments, used both for idempotency and as the history the rules need.
 */
public interface AssessmentStore {

    Optional<StoredAssessment> findByCheckoutId(String checkoutId);

    void save(StoredAssessment assessment);

    /** Payments with the same card between {@code from} and {@code to}, other than the given checkout. */
    int countRecentPaymentsOnCard(String cardToken, Instant from, Instant to, String excludedCheckoutId);

    boolean customerHasHistory(String customerId, String excludedCheckoutId);

    boolean customerUsedDevice(String customerId, String deviceId, String excludedCheckoutId);
}
