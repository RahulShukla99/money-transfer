package com.example.moneytransfer.interfaces.stream;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.application.service.TransferService;
import com.example.moneytransfer.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class TransferStreamConsumerTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private TransferService transferService;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void consumesTransferMessageAndAcknowledgesAfterSuccess() {
        TransferStreamConsumer consumer = new TransferStreamConsumer(transferService);
        TransferStreamMessage message = new TransferStreamMessage(
                FROM,
                TO,
                new BigDecimal("25.00"),
                "stream-request-key"
        );

        when(transferService.transfer(argThat(command ->
                command.fromAccountId().equals(FROM)
                        && command.toAccountId().equals(TO)
                        && command.amount().compareTo(new BigDecimal("25.00")) == 0
                        && command.idempotencyKey().equals("stream-request-key")
        ))).thenReturn(new TransferResult(
                UUID.randomUUID(),
                TransactionStatus.SUCCESS,
                "Transfer completed",
                FROM,
                TO,
                new BigDecimal("25.00"),
                "USD",
                "stream-request-key"
        ));

        consumer.consume(message, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}
