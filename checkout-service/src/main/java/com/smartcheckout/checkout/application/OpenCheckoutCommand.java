package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutLine;
import com.smartcheckout.checkout.domain.ClientContext;
import com.smartcheckout.checkout.domain.PaymentDetails;

import java.util.List;

public record OpenCheckoutCommand(
        String idempotencyKey,
        String storeId,
        Channel channel,
        String customerId,
        String currency,
        List<CheckoutLine> lines,
        PaymentDetails payment,
        ClientContext context
) {
}
