package com.example.bank.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BankArithmeticTest {
    @Test void matchesIndependentDecimalOracleOnBoundaryInputs() {
        long[] balances = {0, 1, 100, 1_000_000_000_000L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long source : balances) for (long target : balances) for (long amount : new long[] {1, 100, Long.MAX_VALUE}) {
            BigDecimal decimalAmount = BigDecimal.valueOf(amount);
            if (source <= Long.MAX_VALUE - amount) {
                assertEquals(BigDecimal.valueOf(source).add(decimalAmount).longValueExact(), BankArithmetic.deposit(source, amount));
            }
            if (amount <= source) {
                assertEquals(BigDecimal.valueOf(source).subtract(decimalAmount).longValueExact(), BankArithmetic.withdraw(source, amount));
                if (target <= Long.MAX_VALUE - amount) {
                    long[] result = BankArithmetic.transfer(source, target, amount);
                    assertEquals(BigDecimal.valueOf(source).add(BigDecimal.valueOf(target)),
                            BigDecimal.valueOf(result[0]).add(BigDecimal.valueOf(result[1])));
                }
            }
        }
    }

    @Test void decimalAdapterMatchesPreviousSemanticsIncludingFallback() {
        Random random = new Random(20261013);
        for (int i = 0; i < 500; i++) {
            BigDecimal source = BigDecimal.valueOf(1 + random.nextLong(1_000_000_000_000L), 2);
            BigDecimal target = BigDecimal.valueOf(random.nextLong(1_000_000_000_000L), 2);
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextLong(source.movePointRight(2).longValueExact()), 2);
            checkAdapter(source, target, amount);
        }
        checkAdapter(new BigDecimal("92233720368547758.07"), new BigDecimal("1.00"), new BigDecimal("0.01"));
        checkAdapter(new BigDecimal("1E+40"), new BigDecimal("1E+40"), new BigDecimal("0.01"));
        checkAdapter(new BigDecimal("0.005"), new BigDecimal("0.001"), new BigDecimal("0.001"));
    }

    private void checkAdapter(BigDecimal source, BigDecimal target, BigDecimal amount) {
        assertEquals(0, source.add(amount).compareTo(DecimalMoneyOperations.deposit(source, amount)));
        assertEquals(0, source.subtract(amount).compareTo(DecimalMoneyOperations.withdraw(source, amount)));
        BigDecimal[] transferred = DecimalMoneyOperations.transfer(source, target, amount);
        assertEquals(0, source.subtract(amount).compareTo(transferred[0]));
        assertEquals(0, target.add(amount).compareTo(transferred[1]));
    }

    @Test void rejectsOverflowAndInsufficientFunds() {
        assertThrows(IllegalArgumentException.class, () -> BankArithmetic.deposit(Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> BankArithmetic.withdraw(0, 1));
        assertThrows(IllegalArgumentException.class, () -> BankArithmetic.transfer(1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> BankArithmetic.deposit(0, 0));
    }

    @Test void rejectedTransferDoesNotPartiallyChangeAccounts() {
        Account source = new Account(1L, "Source", "RUB", new BigDecimal("10.00"), Instant.EPOCH);
        Account target = new Account(2L, "Target", "RUB", new BigDecimal("5.00"), Instant.EPOCH);
        target.block("test", Instant.EPOCH);
        assertThrows(AssertionError.class, () -> source.transferTo(target, new BigDecimal("1.00")));
        assertEquals(0, source.getBalance().compareTo(new BigDecimal("10.00")));
        assertEquals(0, target.getBalance().compareTo(new BigDecimal("5.00")));
    }
}
