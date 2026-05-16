package ru.vkr.verification;

public class ContractViolationException extends IllegalStateException {

    private final ConditionType conditionType;

    public ContractViolationException(ConditionType conditionType, String description) {
        super("%s violated: %s".formatted(conditionType, description));
        this.conditionType = conditionType;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }
}
