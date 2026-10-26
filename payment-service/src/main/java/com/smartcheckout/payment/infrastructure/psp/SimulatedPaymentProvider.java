package com.smartcheckout.payment.infrastructure.psp;

import com.smartcheckout.payment.application.port.AuthorizationRequest;
import com.smartcheckout.payment.application.port.PaymentProvider;
import com.smartcheckout.payment.application.port.PaymentProviderUnavailableException;
import com.smartcheckout.payment.domain.AuthorizationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for a real payment provider, with deterministic test tokens (like PSP sandboxes):
 * <ul>
 *     <li>token containing "insufficient" → declined, INSUFFICIENT_FUNDS</li>
 *     <li>token containing "expired" → declined, EXPIRED_CARD</li>
 *     <li>token containing "stolen" → declined, STOLEN_CARD</li>
 *     <li>amount above 50 000.00 → declined, LIMIT_EXCEEDED</li>
 *     <li>token containing "flaky" → unavailable twice, then approved (shows the retries)</li>
 *     <li>anything else → approved</li>
 * </ul>
 * Like a real provider, it honours the idempotency key: the same key always gets the same answer.
 * (The answers are kept in memory here; a real provider keeps them for days.)
 */
@Component
class SimulatedPaymentProvider implements PaymentProvider {

    static final long LIMIT_MINOR = 5_000_000L;
    private static final int FLAKY_FAILURES = 2;

    private final Map<String, AuthorizationResult> answersByKey = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attemptsByKey = new ConcurrentHashMap<>();
    private final long latencyMs;

    SimulatedPaymentProvider(@Value("${smartcheckout.psp.latency-ms:200}") long latencyMs) {
        this.latencyMs = latencyMs;
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request) {
        simulateLatency();
        AuthorizationResult previous = answersByKey.get(request.idempotencyKey());
        if (previous != null) {
            return previous;
        }
        String token = request.paymentToken() == null ? "" : request.paymentToken();
        if (token.contains("flaky")) {
            int attempt = attemptsByKey.computeIfAbsent(request.idempotencyKey(), key -> new AtomicInteger())
                    .incrementAndGet();
            if (attempt <= FLAKY_FAILURES) {
                throw new PaymentProviderUnavailableException("Provider timeout (attempt " + attempt + ")");
            }
        }
        AuthorizationResult answer = decide(token, request.amountMinor());
        AuthorizationResult stored = answersByKey.putIfAbsent(request.idempotencyKey(), answer);
        return stored != null ? stored : answer;
    }

    private static AuthorizationResult decide(String token, long amountMinor) {
        if (token.contains("insufficient")) {
            return new AuthorizationResult.Declined("INSUFFICIENT_FUNDS", "Insufficient funds");
        }
        if (token.contains("expired")) {
            return new AuthorizationResult.Declined("EXPIRED_CARD", "The card has expired");
        }
        if (token.contains("stolen")) {
            return new AuthorizationResult.Declined("STOLEN_CARD", "The card was reported stolen");
        }
        if (amountMinor > LIMIT_MINOR) {
            return new AuthorizationResult.Declined("LIMIT_EXCEEDED", "The amount exceeds the card limit");
        }
        return new AuthorizationResult.Approved("psp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    private void simulateLatency() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException("Interrupted while calling the provider");
        }
    }
}
