package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.domain.ToolCall;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The tools the model may call during one investigation. A new instance per investigation,
 * so it can record which tools were used (audit trail) without sharing state between threads.
 * <p>
 * Guardrails: read-only calls, arguments validated before any HTTP call (the model's input is
 * never trusted), answers truncated to bound the tokens, and errors returned as data so the
 * model can say "unknown" instead of inventing facts.
 */
public class InvestigationTools {

    static final int MAX_RESULT_LENGTH = 6_000;
    static final int HISTORY_LIMIT = 10;
    private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Za-z0-9._@:-]{1,64}");

    private final ReadOnlyServices services;
    private final List<ToolCall> calls = new ArrayList<>();

    public InvestigationTools(ReadOnlyServices services) {
        this.services = services;
    }

    @Tool(description = "Risk engine decision for a checkout: score, decision, every rule that matched, "
            + "channel, customer id, amount in minor units, currency, card country, IP country and device id.")
    public String getRiskAssessment(@ToolParam(description = "Checkout id (UUID)") String checkoutId) {
        return call("getRiskAssessment", checkoutId,
                () -> isUuid(checkoutId) ? services.riskAssessment(checkoutId.strip()) : null);
    }

    @Tool(description = "The checkout itself: store, channel, customer, status, total, payment method, "
            + "last 4 digits of the card and the purchased lines.")
    public String getCheckout(@ToolParam(description = "Checkout id (UUID)") String checkoutId) {
        return call("getCheckout", checkoutId,
                () -> isUuid(checkoutId) ? services.checkout(checkoutId.strip()) : null);
    }

    @Tool(description = "The customer's last " + HISTORY_LIMIT + " payments seen by the risk engine, most recent "
            + "first: amounts, channels, countries, devices and past decisions. An empty list means a new customer.")
    public String getCustomerHistory(@ToolParam(description = "Customer id, as returned by getRiskAssessment")
                                     String customerId) {
        return call("getCustomerHistory", customerId,
                () -> isCustomerId(customerId) ? services.customerHistory(customerId.strip(), HISTORY_LIMIT) : null);
    }

    /** The tools used so far, in order. */
    public List<ToolCall> calls() {
        return List.copyOf(calls);
    }

    /**
     * @param fetch returns the JSON, empty if not found, or null if the argument is invalid
     */
    private String call(String tool, String input, Supplier<Optional<String>> fetch) {
        long start = System.nanoTime();
        String result;
        boolean succeeded = false;
        try {
            Optional<String> json = fetch.get();
            if (json == null) {
                result = error("INVALID_ARGUMENT", "Invalid identifier: " + abbreviate(input, 80));
            } else if (json.isEmpty()) {
                result = error("NOT_FOUND", "No data for " + abbreviate(input, 80));
            } else {
                result = abbreviate(json.get(), MAX_RESULT_LENGTH);
                succeeded = true;
            }
        } catch (RuntimeException e) {
            // A service down must not stop the investigation: the model is told the data is unavailable.
            result = error("UNAVAILABLE", "The service did not answer");
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        calls.add(new ToolCall(tool, abbreviate(String.valueOf(input), 100), durationMs, succeeded));
        return result;
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value.strip());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isCustomerId(String value) {
        return value != null && CUSTOMER_ID.matcher(value.strip()).matches();
    }

    /** Built by hand on fixed codes and sanitized text, so it is always valid JSON. */
    private static String error(String code, String message) {
        return "{\"error\":\"" + code + "\",\"message\":\"" + message.replaceAll("[\"\\\\\\p{Cntrl}]", "") + "\"}";
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...[truncated]";
    }
}
