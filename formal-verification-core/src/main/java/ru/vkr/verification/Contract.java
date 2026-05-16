package ru.vkr.verification;

public final class Contract {

    private Contract() {
    }

    public static void requires(boolean condition, String description) {
        check(ConditionType.PRECONDITION, condition, description);
    }

    public static <T> T requiresNonNull(T value, String description) {
        requires(value != null, description);
        return value;
    }

    public static void ensures(boolean condition, String description) {
        check(ConditionType.POSTCONDITION, condition, description);
    }

    public static void invariant(boolean condition, String description) {
        check(ConditionType.INVARIANT, condition, description);
    }

    public static void assumes(boolean condition, String description) {
        check(ConditionType.ASSUMPTION, condition, description);
    }

    public static void assertion(boolean condition, String description) {
        check(ConditionType.ASSERTION, condition, description);
    }

    public static boolean holds(boolean condition, String description) {
        assertion(condition, description);
        return true;
    }

    private static void check(ConditionType conditionType, boolean condition, String description) {
        String normalizedDescription = normalizeDescription(description);
        assert condition : "%s violated: %s".formatted(conditionType, normalizedDescription);
        if (!condition) {
            throw new ContractViolationException(conditionType, normalizedDescription);
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "condition must hold";
        }
        return description.trim();
    }
}
