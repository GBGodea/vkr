package com.example.bank.verification;

import ru.vkr.verification.Contract;
import ru.vkr.verification.LongArithmeticSpec;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.Verified;

@Verified(id = "JBMC-BANK-ACCOUNT-MODEL", description = "Simplified account model for bounded verification")
@Invariant("balanceCents is non-negative")
@Invariant("currencyCode is a positive ISO-4217 numeric code")
final class VerifiedAccountModel {

    private long balanceCents;
    private final int currencyCode;
    private final boolean active;

    VerifiedAccountModel(long balanceCents, int currencyCode) {
        Contract.requires(LongArithmeticSpec.isNonNegative(balanceCents), "initial balance must be non-negative");
        Contract.requires(currencyCode > 0 && currencyCode <= 999, "currency code must be ISO-4217-like");
        this.balanceCents = balanceCents;
        this.currencyCode = currencyCode;
        this.active = true;
        verifyInvariant();
    }

    long balanceCents() {
        return balanceCents;
    }

    boolean sameCurrency(VerifiedAccountModel other) {
        return other != null && currencyCode == other.currencyCode;
    }

    @Precondition("account is active, amountCents is positive and addition does not overflow")
    @Postcondition("balanceCents equals old balanceCents plus amountCents")
    void deposit(long amountCents) {
        Contract.requires(active, "deposit account must be active");
        Contract.requires(LongArithmeticSpec.isPositive(amountCents), "deposit amount must be positive");
        Contract.requires(LongArithmeticSpec.canAddWithoutOverflow(balanceCents, amountCents),
                "deposit must not overflow balance");
        verifyInvariant();

        long balanceBefore = balanceCents;
        balanceCents += amountCents;

        assert balanceCents == balanceBefore + amountCents : "deposit postcondition failed";
        Contract.ensures(balanceCents == balanceBefore + amountCents, "deposit must add amount");
        verifyInvariant();
    }

    @Precondition("account is active, amountCents is positive and not greater than balanceCents")
    @Postcondition("balanceCents equals old balanceCents minus amountCents")
    void withdraw(long amountCents) {
        Contract.requires(active, "withdraw account must be active");
        Contract.requires(LongArithmeticSpec.isPositive(amountCents), "withdraw amount must be positive");
        Contract.requires(balanceCents >= amountCents, "withdraw amount must not exceed balance");
        verifyInvariant();

        long balanceBefore = balanceCents;
        balanceCents -= amountCents;

        assert balanceCents == balanceBefore - amountCents : "withdraw postcondition failed";
        assert balanceCents >= 0 : "withdraw must preserve non-negative balance";
        Contract.ensures(balanceCents == balanceBefore - amountCents, "withdraw must subtract amount");
        verifyInvariant();
    }

    /*@
      @ requires target != null;
      @ requires active;
      @ requires target.active;
      @ requires currencyCode == target.currencyCode;
      @ requires amountCents > 0;
      @ requires balanceCents >= amountCents;
      @ requires target.balanceCents <= Long.MAX_VALUE - amountCents;
      @ requires balanceCents <= Long.MAX_VALUE - target.balanceCents;
      @ assignable balanceCents, target.balanceCents;
      @ ensures balanceCents == \old(balanceCents) - amountCents;
      @ ensures target.balanceCents == \old(target.balanceCents) + amountCents;
      @ ensures balanceCents + target.balanceCents == \old(balanceCents + target.balanceCents);
      @ ensures balanceCents >= 0;
      @ ensures target.balanceCents >= 0;
      @*/
    @Verified(id = "JML-BANK-TRANSFER", description = "JML contract example for balance-preserving transfer")
    @Precondition("source and target are active same-currency accounts and source has enough funds")
    @Postcondition("transfer preserves total balance of both accounts")
    void transferTo(VerifiedAccountModel target, long amountCents) {
        Contract.requires(target != null, "target account must be defined");
        Contract.requires(active, "source account must be active");
        Contract.requires(target.active, "target account must be active");
        Contract.requires(sameCurrency(target), "accounts must have same currency");
        Contract.requires(LongArithmeticSpec.isPositive(amountCents), "transfer amount must be positive");
        Contract.requires(balanceCents >= amountCents, "source balance must cover transfer amount");
        Contract.requires(LongArithmeticSpec.canAddWithoutOverflow(target.balanceCents, amountCents),
                "target balance must not overflow");
        Contract.requires(LongArithmeticSpec.canAddWithoutOverflow(balanceCents, target.balanceCents),
                "total balance must not overflow");
        verifyInvariant();
        target.verifyInvariant();

        long sourceBefore = balanceCents;
        long targetBefore = target.balanceCents;
        long totalBefore = sourceBefore + targetBefore;

        withdraw(amountCents);
        target.deposit(amountCents);

        assert balanceCents == sourceBefore - amountCents : "source transfer postcondition failed";
        assert target.balanceCents == targetBefore + amountCents : "target transfer postcondition failed";
        assert balanceCents + target.balanceCents == totalBefore : "transfer must preserve total balance";
        assert balanceCents >= 0 : "source balance must stay non-negative";
        assert target.balanceCents >= 0 : "target balance must stay non-negative";
        Contract.ensures(balanceCents == sourceBefore - amountCents, "source must decrease by amount");
        Contract.ensures(target.balanceCents == targetBefore + amountCents, "target must increase by amount");
        Contract.ensures(balanceCents + target.balanceCents == totalBefore,
                "transfer must preserve total balance");
        verifyInvariant();
        target.verifyInvariant();
    }

    private void verifyInvariant() {
        Contract.invariant(LongArithmeticSpec.isNonNegative(balanceCents), "balance must be non-negative");
        Contract.invariant(currencyCode > 0 && currencyCode <= 999, "currency code must be ISO-4217-like");
    }
}
