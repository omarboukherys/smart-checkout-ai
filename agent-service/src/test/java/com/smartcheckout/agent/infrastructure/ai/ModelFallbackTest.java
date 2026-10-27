package com.smartcheckout.agent.infrastructure.ai;

import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFallbackTest {

    private final List<String> tried = new ArrayList<>();

    @Test
    void an_overloaded_model_hands_over_to_the_next_one() {
        String answer = ModelFallback.firstAvailable(List.of("big-model", "small-model"), model -> {
            tried.add(model);
            if (model.equals("big-model")) {
                throw new RuntimeException("Failed to generate content",
                        new ApiException(503, "UNAVAILABLE", "This model is currently experiencing high demand"));
            }
            return "report by " + model;
        });

        assertThat(answer).isEqualTo("report by small-model");
        assertThat(tried).containsExactly("big-model", "small-model");
    }

    @Test
    void an_exhausted_quota_also_hands_over() {
        String answer = ModelFallback.firstAvailable(List.of("a", "b"), model -> {
            if (model.equals("a")) {
                throw new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded");
            }
            return model;
        });

        assertThat(answer).isEqualTo("b");
    }

    @Test
    void a_permanent_error_stops_at_once() {
        ApiException invalidKey = new ApiException(401, "UNAUTHENTICATED", "API key not valid");

        assertThatThrownBy(() -> ModelFallback.firstAvailable(List.of("a", "b"), model -> {
            tried.add(model);
            throw invalidKey;
        })).isSameAs(invalidKey);
        assertThat(tried).containsExactly("a");
    }

    @Test
    void when_every_model_is_unavailable_the_last_error_is_thrown() {
        assertThatThrownBy(() -> ModelFallback.firstAvailable(List.of("a", "b"), model -> {
            throw new ApiException(503, "UNAVAILABLE", "busy " + model);
        })).hasMessageContaining("busy b");
    }

    @Test
    void recognises_temporary_failures_in_the_cause_chain() {
        assertThat(ModelFallback.isTemporary(new RuntimeException(new SocketTimeoutException("timeout")))).isTrue();
        assertThat(ModelFallback.isTemporary(new ApiException(400, "INVALID_ARGUMENT", "bad request"))).isFalse();
        assertThat(ModelFallback.isTemporary(new IllegalStateException("no cause"))).isFalse();
    }
}
