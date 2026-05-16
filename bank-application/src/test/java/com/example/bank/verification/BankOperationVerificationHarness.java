package com.example.bank.verification;

import org.cprover.CProver;

public final class BankOperationVerificationHarness {

    private static final int RUB = 643;
    private static final long MAX_SYMBOLIC_BALANCE = 1_000_000_000_000L;

    private BankOperationVerificationHarness() {
    }

    public static void main(String[] args) {
        verifyDeposit();
        verifyWithdraw();
        verifyTransfer();
    }

    static void verifyDeposit() {
        long balance = CProver.nondetLong();
        long amount = CProver.nondetLong();
        CProver.assume(balance >= 0 && balance <= MAX_SYMBOLIC_BALANCE);
        CProver.assume(amount > 0 && amount <= MAX_SYMBOLIC_BALANCE);
        CProver.assume(balance <= Long.MAX_VALUE - amount);

        VerifiedAccountModel account = new VerifiedAccountModel(balance, RUB);
        long balanceBefore = account.balanceCents();

        account.deposit(amount);

        assert account.balanceCents() == balanceBefore + amount : "deposit changes balance incorrectly";
        assert account.balanceCents() >= 0 : "deposit creates negative balance";
    }

    static void verifyWithdraw() {
        long balance = CProver.nondetLong();
        long amount = CProver.nondetLong();
        CProver.assume(balance >= 0 && balance <= MAX_SYMBOLIC_BALANCE);
        CProver.assume(amount > 0 && amount <= balance);

        VerifiedAccountModel account = new VerifiedAccountModel(balance, RUB);
        long balanceBefore = account.balanceCents();

        account.withdraw(amount);

        assert account.balanceCents() == balanceBefore - amount : "withdraw changes balance incorrectly";
        assert account.balanceCents() >= 0 : "withdraw creates negative balance";
    }

    static void verifyTransfer() {
        long sourceBalance = CProver.nondetLong();
        long targetBalance = CProver.nondetLong();
        long amount = CProver.nondetLong();
        CProver.assume(sourceBalance >= 0 && sourceBalance <= MAX_SYMBOLIC_BALANCE);
        CProver.assume(targetBalance >= 0 && targetBalance <= MAX_SYMBOLIC_BALANCE);
        CProver.assume(amount > 0 && amount <= sourceBalance);
        CProver.assume(targetBalance <= Long.MAX_VALUE - amount);
        CProver.assume(sourceBalance <= Long.MAX_VALUE - targetBalance);

        VerifiedAccountModel source = new VerifiedAccountModel(sourceBalance, RUB);
        VerifiedAccountModel target = new VerifiedAccountModel(targetBalance, RUB);
        long sourceBefore = source.balanceCents();
        long targetBefore = target.balanceCents();
        long totalBefore = sourceBefore + targetBefore;

        source.transferTo(target, amount);

        assert source.balanceCents() == sourceBefore - amount : "transfer source balance is wrong";
        assert target.balanceCents() == targetBefore + amount : "transfer target balance is wrong";
        assert source.balanceCents() + target.balanceCents() == totalBefore : "transfer does not preserve total";
        assert source.balanceCents() >= 0 : "transfer creates negative source balance";
        assert target.balanceCents() >= 0 : "transfer creates negative target balance";
    }
}
