package ru.vkr.verification;

import java.math.BigDecimal;

public final class BigDecimalSpec {

    private BigDecimalSpec() {
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    public static boolean isEqualByValue(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    public static boolean hasAtMostFractionDigits(BigDecimal value, int fractionDigits) {
        if (value == null || fractionDigits < 0) {
            return false;
        }
        return value.stripTrailingZeros().scale() <= fractionDigits;
    }
}
