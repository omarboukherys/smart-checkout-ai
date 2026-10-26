package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.application.port.CheckoutRepository;
import com.smartcheckout.checkout.application.port.PaymentCommandPublisher;
import com.smartcheckout.checkout.application.port.ProcessedEvents;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.checkout.domain.CheckoutStatus;
import com.smartcheckout.checkout.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Drives the payment saga of a checkout. checkout-service owns the checkout lifecycle, so it decides
 * what happens after the risk decision, and it is the one that applies a human review.
 * <pre>
 * RiskAssessed APPROVE  → AuthorizePayment command (status stays PENDING_PAYMENT)
 * RiskAssessed REVIEW   → ON_HOLD, until a reviewer approves (AuthorizePayment) or rejects (REJECTED)
 * RiskAssessed BLOCK    → REJECTED
 * PaymentAuthorized     → PAID
 * PaymentDeclined       → DECLINED
 * </pre>
 * Every incoming event goes through the inbox: inbox row, state change and outgoing command
 * are committed together, so a duplicate delivery changes nothing.
 */
@Service
@Transactional
public class CheckoutOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrchestrator.class);

    private final CheckoutRepository repository;
    private final ProcessedEvents processedEvents;
    private final PaymentCommandPublisher payments;
    private final Clock clock;

    public CheckoutOrchestrator(CheckoutRepository repository, ProcessedEvents processedEvents,
                                PaymentCommandPublisher payments, Clock clock) {
        this.repository = repository;
        this.processedEvents = processedEvents;
        this.payments = payments;
        this.clock = clock;
    }

    public void onRiskAssessed(UUID eventId, UUID checkoutId, String decision, int score, List<String> reasonCodes) {
        if (!processedEvents.markProcessed(eventId)) {
            return;
        }
        Checkout checkout = repository.findById(checkoutId).orElse(null);
        if (checkout == null || checkout.status() != CheckoutStatus.PENDING_PAYMENT) {
            log.info("Risk decision ignored for checkout {} (not pending)", checkoutId);
            return;
        }
        String summary = "score " + score + " " + reasonCodes;
        switch (decision) {
            case "APPROVE" -> payments.requestAuthorization(checkout);
            case "REVIEW" -> {
                checkout.transitionTo(CheckoutStatus.ON_HOLD, "Held for review: " + summary, clock.instant());
                repository.save(checkout);
            }
            case "BLOCK" -> {
                checkout.transitionTo(CheckoutStatus.REJECTED, "Blocked by the risk engine: " + summary,
                        clock.instant());
                repository.save(checkout);
            }
            // Tolerant reader: a decision added later by risk-service must not break this service.
            default -> log.warn("Unknown risk decision {} for checkout {}", decision, checkoutId);
        }
    }

    public void onPaymentAuthorized(UUID eventId, UUID checkoutId, String pspReference) {
        complete(eventId, checkoutId, CheckoutStatus.PAID, "Authorized by the payment provider (" + pspReference + ")");
    }

    public void onPaymentDeclined(UUID eventId, UUID checkoutId, String reasonCode, String reasonMessage) {
        complete(eventId, checkoutId, CheckoutStatus.DECLINED, reasonCode + ": " + reasonMessage);
    }

    /**
     * Human-in-the-loop: only a checkout held by the risk engine can be reviewed.
     */
    public Checkout review(UUID checkoutId, ReviewDecision decision, String reviewer, String comment) {
        Checkout checkout = repository.findById(checkoutId).orElseThrow(() -> new CheckoutNotFoundException(checkoutId));
        switch (decision) {
            case APPROVE -> {
                checkout.recordReviewApproval(reviewer, clock.instant());
                repository.save(checkout);
                payments.requestAuthorization(checkout);
            }
            case REJECT -> {
                checkout.rejectAfterReview(reviewer, comment, clock.instant());
                repository.save(checkout);
            }
        }
        return checkout;
    }

    private void complete(UUID eventId, UUID checkoutId, CheckoutStatus target, String reason) {
        if (!processedEvents.markProcessed(eventId)) {
            return;
        }
        Checkout checkout = repository.findById(checkoutId).orElse(null);
        if (checkout == null || checkout.isTerminal()) {
            log.info("Payment outcome ignored for checkout {} (unknown or already finished)", checkoutId);
            return;
        }
        try {
            checkout.transitionTo(target, reason, clock.instant());
        } catch (DomainException e) {
            log.warn("Payment outcome rejected for checkout {}: {}", checkoutId, e.getMessage());
            return;
        }
        repository.save(checkout);
    }
}
