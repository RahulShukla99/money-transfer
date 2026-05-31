package com.example.moneytransfer.interfaces.stream;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountStreamActivity(
        Instant occurredAt,
        UUID accountId,
        UUID otherAccountId,
        BigDecimal amount,
        String idempotencyKey,
        AccountStreamActivityDirection direction,
        AccountStreamActivityStage stage,
        String message
) {
}
