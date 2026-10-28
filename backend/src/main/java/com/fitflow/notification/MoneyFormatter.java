package com.fitflow.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyFormatter {

    private MoneyFormatter() {}

    public static String formatPaise(long paise) {
        BigDecimal rupees = BigDecimal.valueOf(paise)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return "Rs " + rupees.toPlainString();
    }
}
