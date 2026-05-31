package com.example.moneytransfer.interfaces.stream;

import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.application.service.TransferService;
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

    private final TransferService transferService;

    public TransferStreamConsumer(TransferService transferService) {
        this.transferService = transferService;
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

        TransferResult result = transferService.transfer(message.toCommand());

        log.info(
                "Stream transfer request completed: transactionId={}, status={}, idempotencyKey={}",
                result.transactionId(),
                result.status(),
                result.idempotencyKey()
        );
        acknowledgment.acknowledge();
    }
}
