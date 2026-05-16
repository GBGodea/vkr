package com.example.bank.service;

import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import ru.vkr.verification.BigDecimalSpec;
import ru.vkr.verification.Contract;

final class MoneyRules {

    private static final int MONEY_SCALE = 2;

    private MoneyRules() {
    }

    static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        Contract.ensures(BigDecimalSpec.isPositive(normalized), "normalized amount must be positive");
        return normalized;
    }

    static BigDecimal requireNonNegative(BigDecimal amount) {
        BigDecimal normalized = normalize(amount);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT, "Amount must be zero or greater");
        }
        Contract.ensures(BigDecimalSpec.isNonNegative(normalized), "normalized amount must be non-negative");
        return normalized;
    }

    private static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new BankException(ErrorCode.INVALID_AMOUNT, "Amount must not be null");
        }
        try {
            BigDecimal normalized = amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            Contract.ensures(BigDecimalSpec.hasAtMostFractionDigits(normalized, MONEY_SCALE),
                    "normalized money must have at most 2 fraction digits");
            return normalized;
        } catch (ArithmeticException ex) {
            throw new BankException(ErrorCode.INVALID_AMOUNT, "Amount must have no more than 2 fraction digits");
        }
    }
}
