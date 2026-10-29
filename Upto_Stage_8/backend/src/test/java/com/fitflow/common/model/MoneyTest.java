package com.fitflow.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    @DisplayName("Money creation and basic accessors")
    void testCreationAndAccessors() {
        Money m = Money.of(49900L);
        assertEquals(49900L, m.paise());
        assertFalse(m.isNegative());
        assertTrue(m.isPositive());
        assertFalse(m.isZero());

        Money zero = Money.zero();
        assertEquals(0L, zero.paise());
        assertTrue(zero.isZero());
        assertFalse(zero.isNegative());
        assertFalse(zero.isPositive());

        Money neg = Money.of(-100L);
        assertTrue(neg.isNegative());
        assertFalse(neg.isPositive());
    }

    @Test
    @DisplayName("add() adds paise exactly and detects overflow")
    void testAdd() {
        Money m1 = Money.of(1000L);
        Money m2 = Money.of(2500L);
        assertEquals(Money.of(3500L), m1.add(m2));
        assertEquals(m1, m1.add(null));

        assertThrows(ArithmeticException.class, () ->
                Money.of(Long.MAX_VALUE).add(Money.of(1L))
        );
    }

    @Test
    @DisplayName("subtract() subtracts paise exactly and detects underflow")
    void testSubtract() {
        Money m1 = Money.of(5000L);
        Money m2 = Money.of(2000L);
        assertEquals(Money.of(3000L), m1.subtract(m2));
        assertEquals(Money.of(-1000L), Money.of(1000L).subtract(Money.of(2000L)));
        assertEquals(m1, m1.subtract(null));

        assertThrows(ArithmeticException.class, () ->
                Money.of(Long.MIN_VALUE).subtract(Money.of(1L))
        );
    }

    @Test
    @DisplayName("compareTo() compares paise values correctly")
    void testCompareTo() {
        Money m1 = Money.of(100L);
        Money m2 = Money.of(200L);
        Money m3 = Money.of(100L);

        assertTrue(m1.compareTo(m2) < 0);
        assertTrue(m2.compareTo(m1) > 0);
        assertEquals(0, m1.compareTo(m3));
    }

    @Test
    @DisplayName("fromBigDecimalHalfUp() rounds .5 upwards to whole paise")
    void testFromBigDecimalHalfUp() {
        assertEquals(Money.zero(), Money.fromBigDecimalHalfUp(null));
        assertEquals(Money.of(10L), Money.fromBigDecimalHalfUp(new BigDecimal("10.4")));
        assertEquals(Money.of(11L), Money.fromBigDecimalHalfUp(new BigDecimal("10.5")));
        assertEquals(Money.of(11L), Money.fromBigDecimalHalfUp(new BigDecimal("10.6")));
        assertEquals(Money.of(2997L), Money.fromBigDecimalHalfUp(new BigDecimal("2996.6666666667")));
        assertEquals(Money.of(44950L), Money.fromBigDecimalHalfUp(new BigDecimal("44950.0000000000")));
    }
}
