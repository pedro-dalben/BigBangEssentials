package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Canonical monetary conversion. Persistence uses the resulting minor units. */
public record Money(long minorUnits, int scale) {
    public Money {
        if (scale < 0 || scale > 18) throw new IllegalArgumentException("Invalid monetary scale");
    }

    public static Money from(BigDecimal amount, int scale, RoundingMode roundingMode, boolean allowNegative) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(roundingMode, "roundingMode");
        BigDecimal normalized;
        try {
            normalized = amount.setScale(scale, roundingMode);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid monetary amount", e);
        }
        if (!allowNegative && normalized.signum() < 0) throw new IllegalArgumentException("Negative amount");
        try {
            return new Money(normalized.movePointRight(scale).longValueExact(), scale);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Monetary amount overflows BIGINT", e);
        }
    }

    public BigDecimal decimal() { return BigDecimal.valueOf(minorUnits, scale); }
}
