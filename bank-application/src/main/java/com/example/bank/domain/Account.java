package com.example.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import ru.vkr.verification.BigDecimalSpec;
import ru.vkr.verification.Contract;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.Verified;

@Verified(id = "BANK-ACCOUNT", description = "Bank account aggregate with executable state invariants")
@Invariant("id, ownerName, currency, balance, status and createdAt are always defined")
@Invariant("balance is never negative")
public class Account {

    private final Long id;
    private final String ownerName;
    private final String currency;
    private BigDecimal balance;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant blockedAt;
    private String blockReason;

    public Account(Long id, String ownerName, String currency, BigDecimal balance, Instant createdAt) {
        Contract.requires(BigDecimalSpec.isNonNegative(balance), "initial account balance must be non-negative");
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        verifyInvariant();
    }

    @Precondition("amount is positive")
    @Postcondition("balance equals old balance plus amount")
    public void deposit(BigDecimal amount) {
        Contract.requires(BigDecimalSpec.isPositive(amount), "deposit amount must be positive");
        verifyInvariant();
        BigDecimal balanceBefore = balance;
        balance = DecimalMoneyOperations.deposit(balance, amount);
        Contract.ensures(BigDecimalSpec.isEqualByValue(balance, balanceBefore.add(amount)),
                "deposit must increase balance by amount");
        verifyInvariant();
    }

    @Precondition("amount is positive and not greater than current balance")
    @Postcondition("balance equals old balance minus amount and remains non-negative")
    public void withdraw(BigDecimal amount) {
        Contract.requires(BigDecimalSpec.isPositive(amount), "withdraw amount must be positive");
        Contract.requires(balance.compareTo(amount) >= 0, "withdraw amount must not exceed balance");
        verifyInvariant();
        BigDecimal balanceBefore = balance;
        balance = DecimalMoneyOperations.withdraw(balance, amount);
        Contract.ensures(BigDecimalSpec.isEqualByValue(balance, balanceBefore.subtract(amount)),
                "withdraw must decrease balance by amount");
        verifyInvariant();
    }

    /** Calculates both new balances before changing the two accounts. Persistence is handled by the service. */
    public void transferTo(Account target, BigDecimal amount) {
        Contract.requires(target != null && target != this, "transfer requires two distinct accounts");
        Contract.requires(BigDecimalSpec.isPositive(amount), "transfer amount must be positive");
        Contract.requires(balance.compareTo(amount) >= 0, "transfer amount must not exceed balance");
        Contract.requires(status == AccountStatus.ACTIVE && target.status == AccountStatus.ACTIVE, "accounts must be active");
        Contract.requires(currency.equals(target.currency), "currencies must match");
        verifyInvariant();
        target.verifyInvariant();
        BigDecimal totalBefore = balance.add(target.balance);
        BigDecimal[] result = DecimalMoneyOperations.transfer(balance, target.balance, amount);
        balance = result[0];
        target.balance = result[1];
        Contract.ensures(BigDecimalSpec.isEqualByValue(balance.add(target.balance), totalBefore), "transfer must preserve total");
        verifyInvariant();
        target.verifyInvariant();
    }

    @Precondition("reason and blockedAt are defined")
    @Postcondition("account status is BLOCKED")
    public void block(String reason, Instant blockedAt) {
        Contract.requires(reason != null && !reason.isBlank(), "block reason must be defined");
        Contract.requires(blockedAt != null, "block time must be defined");
        this.status = AccountStatus.BLOCKED;
        this.blockReason = reason;
        this.blockedAt = blockedAt;
        Contract.ensures(status == AccountStatus.BLOCKED, "blocked account status must be BLOCKED");
        verifyInvariant();
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public String getBlockReason() {
        return blockReason;
    }

    private void verifyInvariant() {
        Contract.invariant(id != null, "account id must be defined");
        Contract.invariant(ownerName != null && !ownerName.isBlank(), "account owner must be defined");
        Contract.invariant(currency != null && currency.matches("[A-Z]{3}"), "account currency must be ISO-like code");
        Contract.invariant(BigDecimalSpec.isNonNegative(balance), "account balance must be non-negative");
        Contract.invariant(status != null, "account status must be defined");
        Contract.invariant(createdAt != null, "account creation time must be defined");
        if (status == AccountStatus.BLOCKED) {
            Contract.invariant(blockedAt != null, "blocked account must have blockedAt");
            Contract.invariant(blockReason != null && !blockReason.isBlank(), "blocked account must have reason");
        }
    }
}
