package com.smartcheckout.checkout.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CheckoutLineEmbeddable {

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    protected CheckoutLineEmbeddable() {
        // required by JPA
    }

    CheckoutLineEmbeddable(String sku, String label, int quantity, long unitPriceMinor) {
        this.sku = sku;
        this.label = label;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
    }

    String sku() {
        return sku;
    }

    String label() {
        return label;
    }

    int quantity() {
        return quantity;
    }

    long unitPriceMinor() {
        return unitPriceMinor;
    }
}
