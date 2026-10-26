package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.application.port.CheckoutEventPublisher;
import com.smartcheckout.checkout.application.port.CheckoutRepository;
import com.smartcheckout.checkout.domain.Checkout;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Use cases of the checkout bounded context. Depends only on the domain and on ports.
 */
@Service
@Transactional
public class CheckoutService {

    private final CheckoutRepository repository;
    private final CheckoutEventPublisher events;
    private final Clock clock;

    public CheckoutService(CheckoutRepository repository, CheckoutEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Opens a checkout. Idempotent: replaying the same Idempotency-Key returns the checkout
     * created the first time instead of charging the customer twice.
     */
    public OpenCheckoutResult open(OpenCheckoutCommand command) {
        return repository.findByIdempotencyKey(command.idempotencyKey())
                .map(existing -> new OpenCheckoutResult(existing, false))
                .orElseGet(() -> createNew(command));
    }

    @Transactional(readOnly = true)
    public Checkout get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CheckoutNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<CheckoutSummary> recent(int limit) {
        return repository.findRecent(limit);
    }

    private OpenCheckoutResult createNew(OpenCheckoutCommand command) {
        Checkout checkout = Checkout.open(
                command.idempotencyKey(), command.storeId(), command.channel(), command.customerId(),
                command.currency(), command.lines(), command.payment(), command.context(), clock.instant());
        repository.save(checkout);
        events.checkoutRequested(checkout);
        return new OpenCheckoutResult(checkout, true);
    }
}
