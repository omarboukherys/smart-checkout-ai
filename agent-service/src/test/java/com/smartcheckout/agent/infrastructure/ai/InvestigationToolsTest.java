package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.domain.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationToolsTest {

    private static final String CHECKOUT_ID = "3f2b8c1e-8d4a-4f6b-9c2d-1a2b3c4d5e6f";

    private final FakeServices services = new FakeServices();
    private final InvestigationTools tools = new InvestigationTools(services);

    @Test
    void returns_the_service_json_and_records_the_call() {
        services.risk = Optional.of("{\"checkoutId\":\"" + CHECKOUT_ID + "\",\"score\":60}");

        String result = tools.getRiskAssessment(CHECKOUT_ID);

        assertThat(result).contains("\"score\":60");
        assertThat(tools.calls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("getRiskAssessment");
            assertThat(call.input()).isEqualTo(CHECKOUT_ID);
            assertThat(call.succeeded()).isTrue();
        });
    }

    @Test
    void an_invalid_id_from_the_model_never_reaches_the_services() {
        String result = tools.getCheckout("../../admin/delete-all");

        assertThat(result).contains("INVALID_ARGUMENT");
        assertThat(services.requests).isEmpty();
        assertThat(tools.calls()).extracting(ToolCall::succeeded).containsExactly(false);
    }

    @Test
    void an_unknown_checkout_is_reported_as_not_found() {
        services.checkout = Optional.empty();

        assertThat(tools.getCheckout(CHECKOUT_ID)).contains("NOT_FOUND");
    }

    @Test
    void a_service_failure_is_returned_to_the_model_as_unavailable_data() {
        services.failure = new IllegalStateException("Connection refused");

        String result = tools.getCustomerHistory("cust-42");

        assertThat(result).contains("UNAVAILABLE").doesNotContain("Connection refused");
        assertThat(tools.calls()).extracting(ToolCall::succeeded).containsExactly(false);
    }

    @Test
    void a_large_answer_is_truncated_to_bound_the_tokens() {
        services.history = Optional.of("x".repeat(InvestigationTools.MAX_RESULT_LENGTH + 500));

        String result = tools.getCustomerHistory("cust-42");

        assertThat(result).hasSizeLessThan(InvestigationTools.MAX_RESULT_LENGTH + 50).endsWith("[truncated]");
        assertThat(services.requests).containsExactly("history:cust-42:" + InvestigationTools.HISTORY_LIMIT);
    }

    @Test
    void records_every_call_in_order() {
        services.risk = Optional.of("{}");
        services.checkout = Optional.of("{}");
        services.history = Optional.of("[]");

        tools.getRiskAssessment(CHECKOUT_ID);
        tools.getCheckout(CHECKOUT_ID);
        tools.getCustomerHistory("cust-42");

        assertThat(tools.calls()).extracting(ToolCall::tool)
                .containsExactly("getRiskAssessment", "getCheckout", "getCustomerHistory");
    }

    private static final class FakeServices implements ReadOnlyServices {

        private final List<String> requests = new ArrayList<>();
        private Optional<String> checkout = Optional.empty();
        private Optional<String> risk = Optional.empty();
        private Optional<String> history = Optional.empty();
        private RuntimeException failure;

        @Override
        public Optional<String> checkout(String checkoutId) {
            return answer("checkout:" + checkoutId, checkout);
        }

        @Override
        public Optional<String> riskAssessment(String checkoutId) {
            return answer("risk:" + checkoutId, risk);
        }

        @Override
        public Optional<String> customerHistory(String customerId, int limit) {
            return answer("history:" + customerId + ":" + limit, history);
        }

        private Optional<String> answer(String request, Optional<String> value) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }
}
