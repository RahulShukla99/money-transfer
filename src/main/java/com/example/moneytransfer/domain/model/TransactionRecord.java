package com.example.moneytransfer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transaction_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_idempotency_key", columnNames = "idempotency_key")
)
public class TransactionRecord {

    @Id
    private UUID id;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionRecord() {
    }

    public TransactionRecord(UUID fromAccountId, UUID toAccountId, Money money, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = money.amount();
        this.currency = money.currency();
        this.idempotencyKey = idempotencyKey;
        this.status = TransactionStatus.PROCESSING;
        this.message = "Transfer is being processed";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void markSuccess() {
        status = TransactionStatus.SUCCESS;
        message = "Transfer completed";
        updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        status = TransactionStatus.FAILED;
        message = reason;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
