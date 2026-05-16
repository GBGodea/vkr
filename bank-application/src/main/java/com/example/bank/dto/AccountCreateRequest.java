package com.example.bank.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank
        @Size(max = 100)
        String ownerName,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO-like 3-letter uppercase code")
        String currency,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal initialBalance
) {
}
