package com.smartcheckout.payment.infrastructure.web;

import com.smartcheckout.payment.application.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only: payments are only ever started by a command from checkout-service.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{checkoutId}")
    ResponseEntity<PaymentResponse> get(@PathVariable String checkoutId) {
        return paymentService.find(checkoutId)
                .map(PaymentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
