package com.example.moneytransfer.interfaces.stream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "money-transfer.stream.kafka", name = "enabled", havingValue = "true")
public class LinkedBlockingTransferStreamQueue implements TransferStreamQueue {

    private final BlockingQueue<TransferStreamWorkItem> queue;

    public LinkedBlockingTransferStreamQueue(
            @Value("${money-transfer.stream.kafka.queue-capacity:1000}") int capacity
    ) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void enqueue(TransferStreamWorkItem workItem) {
        try {
            queue.put(workItem);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueueing transfer stream message", ex);
        }
    }

    @Override
    public TransferStreamWorkItem take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
