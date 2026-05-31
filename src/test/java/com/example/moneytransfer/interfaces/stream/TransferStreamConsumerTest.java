package com.example.moneytransfer.interfaces.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class TransferStreamConsumerTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void consumesTransferMessageByPuttingItOnQueue() throws InterruptedException {
        LinkedBlockingTransferStreamQueue queue = new LinkedBlockingTransferStreamQueue(10);
        TransferStreamConsumer consumer = new TransferStreamConsumer(queue);
        Acknowledgment acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
        TransferStreamMessage message = new TransferStreamMessage(
                FROM,
                TO,
                new BigDecimal("25.00"),
                "stream-request-key"
        );

        consumer.consume(message, acknowledgment);

        TransferStreamWorkItem workItem = queue.take();
        assertThat(workItem.message()).isEqualTo(message);
        assertThat(queue.size()).isZero();
        verify(acknowledgment, org.mockito.Mockito.never()).acknowledge();
    }
}
