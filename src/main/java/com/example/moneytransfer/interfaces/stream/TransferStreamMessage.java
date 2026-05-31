package com.example.moneytransfer.interfaces.stream;

import com.example.moneytransfer.application.dto.TransferCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferStreamMessage(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String idempotencyKey
) {

    TransferCommand toCommand() {
        return new TransferCommand(fromAccountId, toAccountId, amount, idempotencyKey);
    }
}
