package com.smartcheckout.payment.infrastructure.persistence;

import com.smartcheckout.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "checkout_id", length = 64)
    private String checkoutId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 16)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "psp_reference", length = 64)
    private String pspReference;

    @Column(name = "decline_code", length = 32)
    private String declineCode;

    @Column(name = "decline_message")
    private String declineMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected PaymentEntity() {
        // required by JPA
    }

    PaymentEntity(String checkoutId, long amountMinor, String currency, String paymentMethod, Instant createdAt) {
        this.checkoutId = checkoutId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.createdAt = createdAt;
    }

    void applyState(PaymentStatus newStatus, String newPspReference, String newDeclineCode,
                    String newDeclineMessage, Instant newUpdatedAt) {
        this.status = newStatus;
        this.pspReference = newPspReference;
        this.declineCode = newDeclineCode;
        this.declineMessage = newDeclineMessage;
        this.updatedAt = newUpdatedAt;
    }

    String checkoutId() {
        return checkoutId;
    }

    long amountMinor() {
        return amountMinor;
    }

    String currency() {
        return currency;
    }

    String paymentMethod() {
        return paymentMethod;
    }

    PaymentStatus status() {
        return status;
    }

    String pspReference() {
        return pspReference;
    }

    String declineCode() {
        return declineCode;
    }

    String declineMessage() {
        return declineMessage;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
