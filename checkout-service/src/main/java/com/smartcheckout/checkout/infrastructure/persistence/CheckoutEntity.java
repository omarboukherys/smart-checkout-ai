package com.smartcheckout.checkout.infrastructure.persistence;

import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutStatus;
import com.smartcheckout.checkout.domain.PaymentMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping of the checkout table. Kept separate from the domain {@code Checkout}
 * so that persistence concerns (versioning, lazy loading) never leak into business code.
 */
@Entity
@Table(name = "checkout")
public class CheckoutEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "store_id", nullable = false, length = 64)
    private String storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private Channel channel;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_token", nullable = false, length = 128)
    private String paymentToken;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_country", length = 2)
    private String cardCountry;

    @Column(name = "ip_country", length = 2)
    private String ipCountry;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CheckoutStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: two concurrent status updates cannot silently overwrite each other. */
    @Version
    private Long version;

    @ElementCollection
    @CollectionTable(name = "checkout_line", joinColumns = @JoinColumn(name = "checkout_id"))
    @OrderColumn(name = "line_no")
    private List<CheckoutLineEmbeddable> lines = new ArrayList<>();

    protected CheckoutEntity() {
        // required by JPA
    }

    CheckoutEntity(UUID id, String idempotencyKey, String storeId, Channel channel, String customerId,
                   String currency, long totalMinor, PaymentMethod paymentMethod, String paymentToken,
                   String cardLast4, String cardCountry, String ipCountry, String deviceId,
                   Instant createdAt, List<CheckoutLineEmbeddable> lines) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.storeId = storeId;
        this.channel = channel;
        this.customerId = customerId;
        this.currency = currency;
        this.totalMinor = totalMinor;
        this.paymentMethod = paymentMethod;
        this.paymentToken = paymentToken;
        this.cardLast4 = cardLast4;
        this.cardCountry = cardCountry;
        this.ipCountry = ipCountry;
        this.deviceId = deviceId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.lines = new ArrayList<>(lines);
    }

    /** Copies the only fields that change after creation. */
    void applyState(CheckoutStatus newStatus, String newStatusReason, Instant newUpdatedAt) {
        this.status = newStatus;
        this.statusReason = newStatusReason;
        this.updatedAt = newUpdatedAt;
    }

    UUID id() {
        return id;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    String storeId() {
        return storeId;
    }

    Channel channel() {
        return channel;
    }

    String customerId() {
        return customerId;
    }

    String currency() {
        return currency;
    }

    PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    String paymentToken() {
        return paymentToken;
    }

    String cardLast4() {
        return cardLast4;
    }

    String cardCountry() {
        return cardCountry;
    }

    String ipCountry() {
        return ipCountry;
    }

    String deviceId() {
        return deviceId;
    }

    CheckoutStatus status() {
        return status;
    }

    String statusReason() {
        return statusReason;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    List<CheckoutLineEmbeddable> lines() {
        return lines;
    }
}
