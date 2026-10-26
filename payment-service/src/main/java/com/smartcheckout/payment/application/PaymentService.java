package com.smartcheckout.payment.application;

import com.smartcheckout.payment.application.port.AuthorizationRequest;
import com.smartcheckout.payment.application.port.PaymentEventPublisher;
import com.smartcheckout.payment.application.port.PaymentProvider;
import com.smartcheckout.payment.application.port.PaymentRepository;
import com.smartcheckout.payment.domain.AuthorizationResult;
import com.smartcheckout.payment.domain.Payment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * Authorizes a payment, safely under retries:
 * <ol>
 *     <li>the payment is recorded as AUTHORIZING before the provider is called;</li>
 *     <li>the provider is called with the checkout id as idempotency key;</li>
 *     <li>the answer is recorded, then published.</li>
 * </ol>
 * A replay of a completed payment only republishes it. A crash after step 2 leaves the payment
 * AUTHORIZING: the replay calls the provider again with the same key and gets the same answer.
 * <p>
 * Deliberately not {@code @Transactional}: a database transaction must never stay open
 * during a remote call that can take seconds.
 */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentProvider provider;
    private final PaymentEventPublisher publisher;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, PaymentProvider provider, PaymentEventPublisher publisher,
                          Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Payment authorize(AuthorizePaymentCommand command) {
        Payment payment = repository.findByCheckoutId(command.checkoutId())
                .orElseGet(() -> start(command));
        if (!payment.isCompleted()) {
            AuthorizationResult result = provider.authorize(new AuthorizationRequest(
                    payment.checkoutId(), payment.amountMinor(), payment.currency(), command.paymentToken()));
            payment.complete(result, clock.instant());
            repository.update(payment);
        }
        publisher.publish(payment);
        return payment;
    }

    public Optional<Payment> find(String checkoutId) {
        return repository.findByCheckoutId(checkoutId);
    }

    private Payment start(AuthorizePaymentCommand command) {
        Payment payment = Payment.start(command.checkoutId(), command.amountMinor(), command.currency(),
                command.paymentMethod(), clock.instant());
        repository.insert(payment);
        return payment;
    }
}
