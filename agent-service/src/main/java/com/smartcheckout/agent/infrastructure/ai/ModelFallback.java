package com.smartcheckout.agent.infrastructure.ai;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Tries the configured models in order. A model that is temporarily unavailable (overloaded,
 * quota exhausted, too slow) hands over to the next one; any other error (bad request, invalid key,
 * invalid answer) is not the model's availability and stops immediately.
 */
final class ModelFallback {

    private static final Logger log = LoggerFactory.getLogger(ModelFallback.class);

    /** 408 timeout, 429 quota, 5xx provider side (503 = "model experiencing high demand"). */
    private static final Set<Integer> TEMPORARY_HTTP_CODES = Set.of(408, 429, 500, 502, 503, 504);

    private ModelFallback() {
    }

    static <T> T firstAvailable(List<String> models, Function<String, T> attempt) {
        if (models.isEmpty()) {
            throw new IllegalStateException("No AI model configured");
        }
        RuntimeException lastFailure = null;
        for (String model : models) {
            try {
                return attempt.apply(model);
            } catch (RuntimeException e) {
                if (!isTemporary(e)) {
                    throw e;
                }
                log.warn("Model {} is temporarily unavailable: {}", model, rootMessage(e));
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /** Libraries wrap the provider error, so the whole cause chain is inspected. */
    static boolean isTemporary(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ApiException apiError) {
                return TEMPORARY_HTTP_CODES.contains(apiError.code());
            }
            if (cause instanceof GenAiIOException || cause instanceof InterruptedIOException) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.toString();
    }
}
