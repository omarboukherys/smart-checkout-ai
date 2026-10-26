package com.smartcheckout.payment.infrastructure.psp;

import com.smartcheckout.payment.application.port.AuthorizationRequest;
import com.smartcheckout.payment.application.port.PaymentProviderUnavailableException;
import com.smartcheckout.payment.domain.AuthorizationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentProviderTest {

    private final SimulatedPaymentProvider provider = new SimulatedPaymentProvider(0);

    @Test
    void approves_a_normal_payment_with_a_reference() {
        AuthorizationResult result = provider.authorize(request("key-1", "tok_visa_4242", 11_430));

        assertThat(result).isInstanceOf(AuthorizationResult.Approved.class);
        assertThat(((AuthorizationResult.Approved) result).pspReference()).startsWith("psp_");
    }

    @ParameterizedTest
    @CsvSource({
            "tok_insufficient_01, 1000, INSUFFICIENT_FUNDS",
            "tok_expired_01, 1000, EXPIRED_CARD",
            "tok_stolen_0001, 1000, STOLEN_CARD",
            "tok_visa_4242, 6000000, LIMIT_EXCEEDED"
    })
    void declines_the_sandbox_cases(String token, long amountMinor, String expectedCode) {
        AuthorizationResult result = provider.authorize(request("key-" + token, token, amountMinor));

        assertThat(result).isInstanceOfSatisfying(AuthorizationResult.Declined.class,
                declined -> assertThat(declined.code()).isEqualTo(expectedCode));
    }

    @Test
    void the_same_idempotency_key_always_gets_the_same_answer() {
        AuthorizationResult first = provider.authorize(request("key-1", "tok_visa_4242", 11_430));
        AuthorizationResult second = provider.authorize(request("key-1", "tok_visa_4242", 11_430));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void a_flaky_provider_fails_twice_then_answers() {
        AuthorizationRequest request = request("key-flaky", "tok_flaky_01", 11_430);

        assertThatThrownBy(() -> provider.authorize(request)).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> provider.authorize(request)).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(provider.authorize(request)).isInstanceOf(AuthorizationResult.Approved.class);
    }

    private static AuthorizationRequest request(String key, String token, long amountMinor) {
        return new AuthorizationRequest(key, amountMinor, "MAD", token);
    }
}
