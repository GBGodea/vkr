package com.example.bank.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record BlockAccountRequest(
        @NotBlank
        @Size(max = 255)
        String reason
) {
}
