package com.fitflow.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyFormatterTest {

    @ParameterizedTest(name = "Format {0} paise -> {1}")
    @CsvSource({
            "0, Rs 0.00",
            "1, Rs 0.01",
            "99, Rs 0.99",
            "100, Rs 1.00",
            "49900, Rs 499.00",
            "89900, Rs 899.00",
            "149900, Rs 1499.00",
            "100000000, Rs 1000000.00"
    })
    @DisplayName("Format test vectors for paise to Indian Rupee currency strings")
    void testMoneyVectors(long paise, String expected) {
        assertEquals(expected, MoneyFormatter.formatPaise(paise));
    }
}
