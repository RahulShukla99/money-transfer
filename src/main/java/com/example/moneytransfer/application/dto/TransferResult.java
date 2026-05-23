package com.example.moneytransfer.application.dto;

import com.example.moneytransfer.domain.model.TransactionRecord;
import com.example.moneytransfer.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferResult(
        UUID transactionId,
        TransactionStatus status,
        String message,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {

    public static TransferResult from(TransactionRecord record) {
        return new TransferResult(
                record.getId(),
                record.getStatus(),
                record.getMessage(),
                record.getFromAccountId(),
                record.getToAccountId(),
                record.getAmount(),
                record.getCurrency(),
                record.getIdempotencyKey()
        );
    }
}
