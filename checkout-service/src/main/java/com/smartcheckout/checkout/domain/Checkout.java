package com.smartcheckout.checkout.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aggregate root: one payment request coming from a store terminal, the website or the mobile app.
 * <p>
 * Pure Java on purpose (no Spring, JPA or Jackson): the business rules can be read and tested
 * without any infrastructure, and an ArchUnit test keeps it that way.
 */
public final class Checkout {

    /** Upper bound of a single checkout: 100 000.00 in a currency with 2 decimals. */
    public static final long MAX_TOTAL_MINOR = 10_000_000L;

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private static final Map<CheckoutStatus, Set<CheckoutStatus>> ALLOWED_TRANSITIONS = Map.of(
            CheckoutStatus.PENDING_PAYMENT, Set.of(
                    CheckoutStatus.ON_HOLD, CheckoutStatus.PAID, CheckoutStatus.DECLINED,
                    CheckoutStatus.REJECTED, CheckoutStatus.CANCELLED),
            CheckoutStatus.ON_HOLD, Set.of(
                    CheckoutStatus.PAID, CheckoutStatus.DECLINED,
                    CheckoutStatus.REJECTED, CheckoutStatus.CANCELLED));

    private final UUID id;
    private final String idempotencyKey;
    private final String storeId;
    private final Channel channel;
    private final String customerId;
    private final String currency;
    private final List<CheckoutLine> lines;
    private final long totalMinor;
    private final PaymentDetails payment;
    private final ClientContext context;
    private final Instant createdAt;
    private CheckoutStatus status;
    private String statusReason;
    private Instant updatedAt;

    private Checkout(UUID id, String idempotencyKey, String storeId, Channel channel, String customerId,
                     String currency, List<CheckoutLine> lines, PaymentDetails payment, ClientContext context,
                     CheckoutStatus status, String statusReason, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.idempotencyKey = idempotencyKey;
        this.storeId = storeId;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.customerId = customerId;
        this.currency = currency;
        this.lines = List.copyOf(lines);
        this.totalMinor = this.lines.stream().mapToLong(CheckoutLine::totalMinor).reduce(0L, Math::addExact);
        this.payment = Objects.requireNonNull(payment, "payment");
        this.context = context == null ? ClientContext.none() : context;
        this.status = Objects.requireNonNull(status, "status");
        this.statusReason = statusReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Opens a new checkout after checking the business rules.
     */
    public static Checkout open(String idempotencyKey, String storeId, Channel channel, String customerId,
                                String currency, List<CheckoutLine> lines, PaymentDetails payment,
                                ClientContext context, Instant now) {
        requireText(idempotencyKey, "An idempotency key is required");
        requireText(storeId, "A store id is required");
        Objects.requireNonNull(channel, "channel");
        if (channel.requiresCustomerAccount() && (customerId == null || customerId.isBlank())) {
            throw new DomainException("An identified customer is required on channel " + channel);
        }
        if (currency == null || !CURRENCY.matcher(currency).matches()) {
            throw new DomainException("Currency must be an ISO 4217 code such as MAD");
        }
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("A checkout needs at least one line");
        }
        Checkout checkout = new Checkout(UUID.randomUUID(), idempotencyKey, storeId, channel, customerId,
                currency, lines, payment, context, CheckoutStatus.PENDING_PAYMENT, null, now, now);
        if (checkout.totalMinor > MAX_TOTAL_MINOR) {
            throw new DomainException("Checkout total exceeds the maximum allowed amount");
        }
        return checkout;
    }

    /**
     * Rebuilds a checkout from storage. No business validation: the data was valid when it was saved.
     */
    public static Checkout rehydrate(UUID id, String idempotencyKey, String storeId, Channel channel,
                                     String customerId, String currency, List<CheckoutLine> lines,
                                     PaymentDetails payment, ClientContext context, CheckoutStatus status,
                                     String statusReason, Instant createdAt, Instant updatedAt) {
        return new Checkout(id, idempotencyKey, storeId, channel, customerId, currency, lines, payment,
                context, status, statusReason, createdAt, updatedAt);
    }

    /**
     * Moves the checkout to a new status. Terminal statuses (PAID, DECLINED, REJECTED, CANCELLED)
     * cannot change anymore, which protects against late or replayed events.
     */
    public void transitionTo(CheckoutStatus target, String reason, Instant at) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new DomainException("Transition " + status + " -> " + target + " is not allowed");
        }
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = at;
    }

    public boolean isTerminal() {
        return !ALLOWED_TRANSITIONS.containsKey(status);
    }

    public int itemCount() {
        return lines.stream().mapToInt(CheckoutLine::quantity).sum();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(message);
        }
    }

    public UUID id() {
        return id;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String storeId() {
        return storeId;
    }

    public Channel channel() {
        return channel;
    }

    public String customerId() {
        return customerId;
    }

    public String currency() {
        return currency;
    }

    public List<CheckoutLine> lines() {
        return lines;
    }

    public long totalMinor() {
        return totalMinor;
    }

    public PaymentDetails payment() {
        return payment;
    }

    public ClientContext context() {
        return context;
    }

    public CheckoutStatus status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
