package com.fitflow.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable value object representing monetary amounts in paise (BIGINT, 1/100 INR).
 * Complies with Rule R4 (No floating point arithmetic for money).
 */
public record Money(long paise) implements Comparable<Money> {

    public static Money of(long paise) {
        return new Money(paise);
    }

    public static Money zero() {
        return new Money(0L);
    }

    /**
     * Converts a BigDecimal value in paise to whole paise using HALF_UP rounding.
     * Rounding occurs ONCE at the end of ratio calculations.
     */
    public static Money fromBigDecimalHalfUp(BigDecimal paiseValue) {
        if (paiseValue == null) {
            return Money.zero();
        }
        return new Money(paiseValue.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    public Money add(Money other) {
        if (other == null) {
            return this;
        }
        return new Money(Math.addExact(this.paise, other.paise));
    }

    public Money subtract(Money other) {
        if (other == null) {
            return this;
        }
        return new Money(Math.subtractExact(this.paise, other.paise));
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.paise, other.paise);
    }

    public boolean isNegative() {
        return this.paise < 0;
    }

    public boolean isZero() {
        return this.paise == 0;
    }

    public boolean isPositive() {
        return this.paise > 0;
    }
}
