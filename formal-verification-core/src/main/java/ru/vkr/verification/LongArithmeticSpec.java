package ru.vkr.verification;

public final class LongArithmeticSpec {

    private LongArithmeticSpec() {
    }

    public static boolean isPositive(long value) {
        return value > 0;
    }

    public static boolean isNonNegative(long value) {
        return value >= 0;
    }

    public static boolean canAddWithoutOverflow(long left, long right) {
        if (right > 0) {
            return left <= Long.MAX_VALUE - right;
        }
        if (right < 0) {
            return left >= Long.MIN_VALUE - right;
        }
        return true;
    }

    public static boolean canSubtractWithoutOverflow(long left, long right) {
        if (right > 0) {
            return left >= Long.MIN_VALUE + right;
        }
        if (right < 0) {
            return left <= Long.MAX_VALUE + right;
        }
        return true;
    }
}
