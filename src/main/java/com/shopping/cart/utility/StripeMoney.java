package com.shopping.cart.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Catalog prices are stored in MMK (matches the Pixel Tech storefront).
 * Stripe Checkout for this project charges in SGD using a fixed demo FX rate.
 */
public final class StripeMoney {
    /** Demo bank rate used by the storefront USD toggle as well. */
    public static final BigDecimal MMK_PER_SGD = BigDecimal.valueOf(3500);

    private StripeMoney() {}

    /** Convert an MMK catalog price to Stripe SGD cents. */
    public static long mmkToSgdCents(BigDecimal mmkAmount) {
        if (mmkAmount == null || mmkAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        BigDecimal sgd = mmkAmount.divide(MMK_PER_SGD, 2, RoundingMode.HALF_UP);
        return sgd.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Convert Stripe SGD cents back to catalog MMK for order persistence. */
    public static BigDecimal sgdCentsToMmk(long sgdCents) {
        BigDecimal sgd = BigDecimal.valueOf(sgdCents).movePointLeft(2);
        return sgd.multiply(MMK_PER_SGD).setScale(2, RoundingMode.HALF_UP);
    }
}
