package com.example.bank.dto;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String ownerName,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt,
        Instant blockedAt,
        String blockReason
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getBlockedAt(),
                account.getBlockReason()
        );
    }
}
