package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.application.CheckoutOrchestrator;
import com.smartcheckout.checkout.application.CheckoutService;
import com.smartcheckout.checkout.application.OpenCheckoutResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkouts")
class CheckoutController {

    private final CheckoutService checkoutService;
    private final CheckoutOrchestrator orchestrator;

    CheckoutController(CheckoutService checkoutService, CheckoutOrchestrator orchestrator) {
        this.checkoutService = checkoutService;
        this.orchestrator = orchestrator;
    }

    /**
     * Opens a checkout. 201 the first time, 200 when the same Idempotency-Key is replayed.
     */
    @PostMapping
    ResponseEntity<CheckoutResponse> open(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateCheckoutRequest request) {
        OpenCheckoutResult result = checkoutService.open(request.toCommand(idempotencyKey));
        CheckoutResponse body = CheckoutResponse.from(result.checkout());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/checkouts/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    CheckoutResponse get(@PathVariable UUID id) {
        return CheckoutResponse.from(checkoutService.get(id));
    }

    /**
     * Human review of a checkout held by the risk engine: APPROVE sends it to payment, REJECT refuses it.
     * 422 if the checkout is not on hold.
     */
    @PostMapping("/{id}/review")
    CheckoutResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return CheckoutResponse.from(orchestrator.review(id, request.decision(), request.reviewer(), request.comment()));
    }

    @GetMapping
    List<CheckoutSummaryResponse> recent(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return checkoutService.recent(limit).stream()
                .map(CheckoutSummaryResponse::from)
                .toList();
    }
}
