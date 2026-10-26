package com.smartcheckout.checkout.infrastructure.web;

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

    CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
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

    @GetMapping
    List<CheckoutSummaryResponse> recent(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return checkoutService.recent(limit).stream()
                .map(CheckoutSummaryResponse::from)
                .toList();
    }
}
