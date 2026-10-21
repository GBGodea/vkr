package examples;

import ru.vkr.verification.annotation.GenerateVerification;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.Verified;

/** Negative examples compiled separately; never included in the application. */
public final class Stage3Examples {
    @GenerateVerification(max = 1000)
    @Verified(id = "DEMO-BROKEN-WITHDRAW")
    @Precondition("balance >= amount && amount > 0")
    @Postcondition("result == old(balance) - amount")
    @Invariant("result >= 0")
    public static long brokenWithdraw(long balance, long amount) {
        return balance + amount; // deliberate defect: adds instead of subtracting
    }

    @GenerateVerification(max = 1000)
    @Verified(id = "DEMO-CONTRADICTORY-PRECONDITIONS")
    @Precondition("amount > 100 && amount < 50")
    @Postcondition("result == amount")
    public static long impossible(long amount) { return amount; }

    @GenerateVerification(max = 1000)
    @Verified(id = "DEMO-PRECONDITION-OUTSIDE-BOUNDS")
    @Precondition("amount > 1000")
    @Postcondition("result == amount")
    public static long outsideBounds(long amount) { return amount; }
}
