package com.smartcheckout.checkout.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests of the business rules: no Spring, no database, they run in milliseconds.
 */
class CheckoutTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final PaymentDetails CARD = new PaymentDetails(PaymentMethod.CARD, "tok_visa_4242", "4242", "MA");
    private static final List<CheckoutLine> BASKET = List.of(
            new CheckoutLine("SKU-CAFE-500", "Cafe moulu 500 g", 2, 4_590),
            new CheckoutLine("SKU-THE-250", "The vert 250 g", 1, 2_250));

    @Test
    void computes_the_total_in_minor_units() {
        Checkout checkout = openAtStore();

        assertThat(checkout.totalMinor()).isEqualTo(11_430L);
        assertThat(checkout.itemCount()).isEqualTo(3);
        assertThat(checkout.status()).isEqualTo(CheckoutStatus.PENDING_PAYMENT);
        assertThat(checkout.createdAt()).isEqualTo(NOW);
    }

    @Test
    void accepts_an_anonymous_customer_at_a_store_terminal() {
        assertThat(openAtStore().customerId()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Channel.class, names = {"WEB", "MOBILE"})
    void requires_an_identified_customer_online(Channel channel) {
        assertThatThrownBy(() -> Checkout.open("key-1", "CASA-01", channel, null, "MAD", BASKET, CARD,
                ClientContext.none(), NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("identified customer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "4111 1111 1111 1111", "4111-1111-1111-1111"})
    void refuses_raw_card_numbers(String rawCardNumber) {
        assertThatThrownBy(() -> new PaymentDetails(PaymentMethod.CARD, rawCardNumber, "1111", "MA"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PCI-DSS");
    }

    @Test
    void refuses_an_empty_basket() {
        assertThatThrownBy(() -> Checkout.open("key-1", "CASA-01", Channel.POS, null, "MAD", List.of(), CARD,
                ClientContext.none(), NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void refuses_a_total_above_the_maximum() {
        List<CheckoutLine> tooExpensive = List.of(new CheckoutLine("SKU-GOLD", "Lingot", 2, 6_000_000));

        assertThatThrownBy(() -> Checkout.open("key-1", "CASA-01", Channel.POS, null, "MAD", tooExpensive, CARD,
                ClientContext.none(), NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void a_paid_checkout_can_no_longer_change() {
        Checkout checkout = openAtStore();
        Instant paidAt = NOW.plusSeconds(5);

        checkout.transitionTo(CheckoutStatus.PAID, null, paidAt);

        assertThat(checkout.isTerminal()).isTrue();
        assertThat(checkout.updatedAt()).isEqualTo(paidAt);
        assertThatThrownBy(() -> checkout.transitionTo(CheckoutStatus.DECLINED, "late event", NOW.plusSeconds(9)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PAID -> DECLINED");
    }

    @Test
    void a_checkout_on_hold_can_still_be_approved_by_a_reviewer() {
        Checkout checkout = openAtStore();

        checkout.transitionTo(CheckoutStatus.ON_HOLD, "Unusual amount", NOW.plusSeconds(1));
        checkout.transitionTo(CheckoutStatus.PAID, "Approved by reviewer", NOW.plusSeconds(60));

        assertThat(checkout.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(checkout.statusReason()).isEqualTo("Approved by reviewer");
    }

    private static Checkout openAtStore() {
        return Checkout.open("key-1", "CASA-01", Channel.POS, null, "MAD", BASKET, CARD, ClientContext.none(), NOW);
    }
}
