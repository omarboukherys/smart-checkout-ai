package com.smartcheckout.checkout.domain;

/**
 * One basket line. Prices are in minor units (centimes) to avoid floating point errors.
 */
public record CheckoutLine(String sku, String label, int quantity, long unitPriceMinor) {

    /** Bounds that also make overflow impossible when totals are computed. */
    public static final int MAX_QUANTITY = 10_000;
    public static final long MAX_UNIT_PRICE_MINOR = 1_000_000_000L;

    public CheckoutLine {
        if (sku == null || sku.isBlank()) {
            throw new DomainException("Line SKU is required");
        }
        if (label == null || label.isBlank()) {
            throw new DomainException("Line label is required");
        }
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new DomainException("Line quantity must be between 1 and " + MAX_QUANTITY);
        }
        if (unitPriceMinor <= 0 || unitPriceMinor > MAX_UNIT_PRICE_MINOR) {
            throw new DomainException("Line unit price must be positive and at most " + MAX_UNIT_PRICE_MINOR);
        }
    }

    public long totalMinor() {
        return Math.multiplyExact(unitPriceMinor, (long) quantity);
    }
}
