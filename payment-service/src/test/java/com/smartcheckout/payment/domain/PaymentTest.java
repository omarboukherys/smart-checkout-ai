package com.smartcheckout.payment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    @Test
    void a_new_payment_is_being_authorized() {
        Payment payment = Payment.start("chk-1", 11_430, "MAD", "CARD", NOW);

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZING);
        assertThat(payment.isCompleted()).isFalse();
    }

    @Test
    void an_approval_stores_the_provider_reference() {
        Payment payment = Payment.start("chk-1", 11_430, "MAD", "CARD", NOW);

        payment.complete(new AuthorizationResult.Approved("psp_abc"), NOW.plusSeconds(1));

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.pspReference()).isEqualTo("psp_abc");
        assertThat(payment.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void a_decline_stores_its_reason() {
        Payment payment = Payment.start("chk-1", 11_430, "MAD", "CARD", NOW);

        payment.complete(new AuthorizationResult.Declined("INSUFFICIENT_FUNDS", "Insufficient funds"), NOW);

        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.declineCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void a_completed_payment_cannot_be_completed_again() {
        Payment payment = Payment.start("chk-1", 11_430, "MAD", "CARD", NOW);
        payment.complete(new AuthorizationResult.Approved("psp_abc"), NOW);

        assertThatThrownBy(() -> payment.complete(new AuthorizationResult.Declined("X", "late"), NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already AUTHORIZED");
    }

    @Test
    void refuses_a_zero_amount() {
        assertThatThrownBy(() -> Payment.start("chk-1", 0, "MAD", "CARD", NOW))
                .isInstanceOf(DomainException.class);
    }
}
