package com.smartcheckout.checkout.infrastructure.web;

import java.math.BigDecimal;

final class Amounts {

    private Amounts() {
    }

    /** 11430 minor units → "114.30" (currencies with 2 decimals, such as MAD or EUR). */
    static String format(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }
}
