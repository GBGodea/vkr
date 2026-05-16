package com.example.bank.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public record TransferRequest(
        @NotNull
        @Positive
        Long sourceAccountId,

        @NotNull
        @Positive
        Long targetAccountId,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount
) {
}
