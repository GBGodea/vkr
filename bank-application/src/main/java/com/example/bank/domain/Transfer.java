package com.example.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Transfer {

    private final UUID id;
    private final Long sourceAccountId;
    private final Long targetAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final TransferStatus status;
    private final Instant createdAt;

    public Transfer(
            UUID id,
            Long sourceAccountId,
            Long targetAccountId,
            BigDecimal amount,
            String currency,
            TransferStatus status,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        this.targetAccountId = Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
