package com.example.bank.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

public record MoneyOperationRequest(
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount
) {
}
