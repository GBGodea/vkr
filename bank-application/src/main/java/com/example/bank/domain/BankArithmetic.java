package com.example.bank.domain;

import ru.vkr.verification.annotation.GenerateVerification;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.Verified;

/** Production arithmetic in minor currency units. Also the direct target of JBMC. */
public final class BankArithmetic {
    private BankArithmetic() { }

    @GenerateVerification
    @Verified(id = "BANK-DEPOSIT-CENTS")
    @Precondition("balance >= 0 && amount > 0")
    @Precondition("balance <= Long.MAX_VALUE - amount")
    @Postcondition("result == old(balance) + amount")
    @Invariant("result >= 0")
    public static long deposit(long balance, long amount) {
        if (balance < 0 || amount <= 0 || balance > Long.MAX_VALUE - amount) throw new IllegalArgumentException("Invalid deposit");
        return balance + amount;
    }

    @GenerateVerification
    @Verified(id = "BANK-WITHDRAW-CENTS")
    @Precondition("balance >= 0 && amount > 0 && amount <= balance")
    @Postcondition("result == old(balance) - amount")
    @Invariant("result >= 0")
    public static long withdraw(long balance, long amount) {
        if (balance < 0 || amount <= 0 || amount > balance) throw new IllegalArgumentException("Invalid withdrawal");
        return balance - amount;
    }

    @GenerateVerification
    @Verified(id = "BANK-TRANSFER-CENTS")
    @Precondition("source >= 0 && target >= 0 && amount > 0 && amount <= source")
    @Precondition("target <= Long.MAX_VALUE - amount")
    @Precondition("source <= Long.MAX_VALUE - target")
    @Postcondition("result != null && result.length == 2")
    @Postcondition("result[0] == old(source) - amount")
    @Postcondition("result[1] == old(target) + amount")
    @Postcondition("result[0] + result[1] == old(source + target)")
    @Invariant("result[0] >= 0 && result[1] >= 0")
    public static long[] transfer(long source, long target, long amount) {
        if (source < 0 || target < 0 || amount <= 0 || amount > source || target > Long.MAX_VALUE - amount) {
            throw new IllegalArgumentException("Invalid transfer");
        }
        return new long[] {withdraw(source, amount), deposit(target, amount)};
    }
}
