package com.example.moneytransfer.interfaces.stream;

import com.example.moneytransfer.application.dto.TransferResult;
import com.example.moneytransfer.application.service.TransferService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "money-transfer.stream.kafka", name = "enabled", havingValue = "true")
public class TransferStreamWorker {

    private static final Logger log = LoggerFactory.getLogger(TransferStreamWorker.class);

    private final TransferStreamQueue transferStreamQueue;
    private final TransferService transferService;
    private final AccountStreamActivityTracker activityTracker;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public TransferStreamWorker(
            TransferStreamQueue transferStreamQueue,
            TransferService transferService,
            AccountStreamActivityTracker activityTracker
    ) {
        this.transferStreamQueue = transferStreamQueue;
        this.transferService = transferService;
        this.activityTracker = activityTracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executorService.submit(this::processLoop);
        log.info("Transfer stream worker started");
    }

    void processNext() throws InterruptedException {
        TransferStreamWorkItem workItem = transferStreamQueue.take();
        TransferStreamMessage message = workItem.message();

        activityTracker.recordProcessing(message);

        TransferResult result;
        try {
            result = transferService.transfer(message.toCommand());
            activityTracker.recordSuccess(message);
        } catch (RuntimeException ex) {
            activityTracker.recordFailed(message, ex.getMessage());
            throw ex;
        }

        log.info(
                "Stream transfer request completed: transactionId={}, status={}, idempotencyKey={}, queuedMessages={}",
                result.transactionId(),
                result.status(),
                result.idempotencyKey(),
                transferStreamQueue.size()
        );
        workItem.acknowledgment().acknowledge();
    }

    @PreDestroy
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    private void processLoop() {
        while (running) {
            try {
                processNext();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (RuntimeException ex) {
                log.error("Stream transfer processing failed; Kafka message will remain unacknowledged", ex);
            }
        }
    }
}
