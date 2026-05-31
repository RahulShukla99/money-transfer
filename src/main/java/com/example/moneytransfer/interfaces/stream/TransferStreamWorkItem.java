package com.example.moneytransfer.interfaces.stream;

import org.springframework.kafka.support.Acknowledgment;

public record TransferStreamWorkItem(
        TransferStreamMessage message,
        Acknowledgment acknowledgment
) {
}
