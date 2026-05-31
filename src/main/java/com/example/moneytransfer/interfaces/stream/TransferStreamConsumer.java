package com.example.moneytransfer.interfaces.stream;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConditionalOnProperty(prefix = "money-transfer.stream.kafka", name = "enabled", havingValue = "true")
public class TransferStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferStreamConsumer.class);

    private final TransferStreamQueue transferStreamQueue;

    public TransferStreamConsumer(TransferStreamQueue transferStreamQueue) {
        this.transferStreamQueue = transferStreamQueue;
    }

    @KafkaListener(
            topics = "${money-transfer.stream.kafka.transfer-requests-topic}",
            groupId = "${money-transfer.stream.kafka.consumer-group}"
    )
    public void consume(@Valid TransferStreamMessage message, Acknowledgment acknowledgment) {
        log.info(
                "Stream transfer request received: fromAccountId={}, toAccountId={}, amount={}, idempotencyKey={}",
                message.fromAccountId(),
                message.toAccountId(),
                message.amount(),
                message.idempotencyKey()
        );
        transferStreamQueue.enqueue(new TransferStreamWorkItem(message, acknowledgment));
    }
}
