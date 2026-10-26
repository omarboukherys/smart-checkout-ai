package com.smartcheckout.agent.infrastructure.client;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.application.port.RiskDecisionLookup;
import com.smartcheckout.agent.infrastructure.ai.ReadOnlyServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * HTTP clients to the other services' read APIs (GET only). Short timeouts: a slow service
 * must not hold a Kafka consumer, the agent simply reports the data as unavailable.
 */
@Component
class ServiceClients implements ReadOnlyServices, RiskDecisionLookup {

    private final RestClient checkout;
    private final RestClient risk;
    private final JsonMapper jsonMapper;

    ServiceClients(@Value("${smartcheckout.services.checkout-url}") String checkoutUrl,
                   @Value("${smartcheckout.services.risk-url}") String riskUrl,
                   @Value("${smartcheckout.http.timeout-ms:3000}") long timeoutMs,
                   JsonMapper jsonMapper) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        this.checkout = restClient(checkoutUrl, timeout);
        this.risk = restClient(riskUrl, timeout);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<String> checkout(String checkoutId) {
        return get(() -> checkout.get().uri("/api/v1/checkouts/{id}", checkoutId).retrieve().body(String.class));
    }

    @Override
    public Optional<String> riskAssessment(String checkoutId) {
        return get(() -> risk.get().uri("/api/v1/risk-assessments/{id}", checkoutId).retrieve().body(String.class));
    }

    @Override
    public Optional<String> customerHistory(String customerId, int limit) {
        return get(() -> risk.get()
                .uri("/api/v1/risk-assessments?customerId={customerId}&limit={limit}", customerId, limit)
                .retrieve().body(String.class));
    }

    /** The risk decision of a checkout, to investigate it again on the supervisor's request. */
    @Override
    public Optional<InvestigationRequest> find(String checkoutId) {
        return riskAssessment(checkoutId)
                .map(json -> jsonMapper.readValue(json, RiskView.class))
                .map(view -> new InvestigationRequest(view.checkoutId(), view.score(),
                        view.reasons() == null ? List.of() : view.reasons().stream().map(RiskView.Reason::code).toList()));
    }

    /** Only the fields needed here; the others are ignored (tolerant reader). */
    record RiskView(String checkoutId, int score, String decision, List<Reason> reasons) {

        record Reason(String code, int points, String detail) {
        }
    }

    private static Optional<String> get(Supplier<String> request) {
        try {
            return Optional.ofNullable(request.get());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private static RestClient restClient(String baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
