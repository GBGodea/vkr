package com.example.bank.dto;

import com.example.bank.domain.Transfer;
import com.example.bank.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getTargetAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getCreatedAt()
        );
    }
}
