package com.smartcheckout.agent.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces the Gemini client built by Spring AI, to bound the waiting time. By default the Google SDK
 * retries a 503 up to 5 times with growing delays, with no timeout: 79 s before giving up in our tests.
 * Here: one retry, then the next model (ModelFallback), then the rules. A supervisor is not kept waiting.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
class GeminiClientConfig {

    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key:}") String apiKey,
                             @Value("${smartcheckout.agent.gemini.timeout-ms:20000}") int timeoutMs,
                             @Value("${smartcheckout.agent.gemini.attempts:2}") int attempts) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("AI_CHAT_MODEL=google-genai but GEMINI_API_KEY is empty");
        }
        HttpOptions httpOptions = HttpOptions.builder()
                // Per model call, retries included (one investigation = about 4 calls).
                .timeout(timeoutMs)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(attempts)
                        .initialDelay(1.0)
                        .maxDelay(4.0)
                        .build())
                .build();
        return Client.builder().apiKey(apiKey).httpOptions(httpOptions).build();
    }
}
