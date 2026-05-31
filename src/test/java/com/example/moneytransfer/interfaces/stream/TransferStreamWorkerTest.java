package com.example.moneytransfer.interfaces.stream;

import static org.mockito.ArgumentMatchers.argThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.application.service.TransferService;
import com.example.moneytransfer.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class TransferStreamWorkerTest {

    private static final UUID FROM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void processesQueuedTransferMessageAndAcknowledgesAfterSuccess() throws InterruptedException {
        LinkedBlockingTransferStreamQueue queue = new LinkedBlockingTransferStreamQueue(10);
        AccountStreamActivityTracker activityTracker = new AccountStreamActivityTracker();
        TransferService transferService = org.mockito.Mockito.mock(TransferService.class);
        Acknowledgment acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
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

        queue.enqueue(new TransferStreamWorkItem(message, acknowledgment));
        TransferStreamWorker worker = new TransferStreamWorker(queue, transferService, activityTracker);

        worker.processNext();

        verify(acknowledgment).acknowledge();

        List<AccountStreamActivity> fromActivities = activityTracker.findActivitySince(FROM, Instant.EPOCH);
        assertThat(fromActivities)
                .extracting(AccountStreamActivity::direction, AccountStreamActivity::stage)
                .containsExactly(
                        tuple(AccountStreamActivityDirection.OUTGOING, AccountStreamActivityStage.PROCESSING),
                        tuple(AccountStreamActivityDirection.OUTGOING, AccountStreamActivityStage.SUCCESS)
                );
    }
}
