package com.smartcheckout.payment.infrastructure.messaging;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.payment.AuthorizePayment;
import com.smartcheckout.contracts.payment.PaymentCommand;
import com.smartcheckout.payment.application.AuthorizePaymentCommand;
import com.smartcheckout.payment.application.PaymentService;
import com.smartcheckout.payment.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class PaymentCommandsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandsListener.class);

    private final PaymentService paymentService;
    private final JsonMapper jsonMapper;

    PaymentCommandsListener(PaymentService paymentService, JsonMapper jsonMapper) {
        this.paymentService = paymentService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS)
    public void onCommand(String payload) {
        PaymentCommand command = jsonMapper.readValue(payload, PaymentCommand.class);
        switch (command) {
            case AuthorizePayment authorize -> {
                Payment payment = paymentService.authorize(new AuthorizePaymentCommand(
                        authorize.checkoutId().toString(), authorize.amountMinor(), authorize.currency(),
                        authorize.paymentMethod(), authorize.paymentToken()));
                log.info("Payment for checkout {} -> {}{}", payment.checkoutId(), payment.status(),
                        payment.declineCode() == null ? "" : " (" + payment.declineCode() + ")");
            }
        }
    }
}
