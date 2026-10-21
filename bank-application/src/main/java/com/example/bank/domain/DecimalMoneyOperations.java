package com.example.bank.domain;

import java.math.BigDecimal;

/** Exact conversion at the application boundary; preserves the previous BigDecimal domain. */
final class DecimalMoneyOperations {
    private DecimalMoneyOperations() { }

    static BigDecimal deposit(BigDecimal balance, BigDecimal amount) {
        Long b = cents(balance), a = cents(amount);
        if (b != null && a != null && b >= 0 && a > 0 && b <= Long.MAX_VALUE - a) {
            return decimal(BankArithmetic.deposit(b, a));
        }
        // Values outside the verified cents domain keep the original arbitrary precision behavior.
        return balance.add(amount);
    }

    static BigDecimal withdraw(BigDecimal balance, BigDecimal amount) {
        Long b = cents(balance), a = cents(amount);
        if (b != null && a != null && b >= 0 && a > 0 && a <= b) {
            return decimal(BankArithmetic.withdraw(b, a));
        }
        return balance.subtract(amount);
    }

    static BigDecimal[] transfer(BigDecimal source, BigDecimal target, BigDecimal amount) {
        Long s = cents(source), t = cents(target), a = cents(amount);
        if (s != null && t != null && a != null && s >= 0 && t >= 0 && a > 0 && a <= s && t <= Long.MAX_VALUE - a) {
            long[] result = BankArithmetic.transfer(s, t, a);
            return new BigDecimal[] {decimal(result[0]), decimal(result[1])};
        }
        return new BigDecimal[] {source.subtract(amount), target.add(amount)};
    }

    private static Long cents(BigDecimal value) {
        try { return value.movePointRight(2).longValueExact(); }
        catch (ArithmeticException ex) { return null; }
    }

    private static BigDecimal decimal(long cents) { return BigDecimal.valueOf(cents, 2); }
}
