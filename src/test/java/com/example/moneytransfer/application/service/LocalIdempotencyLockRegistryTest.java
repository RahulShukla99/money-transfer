package com.example.moneytransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalIdempotencyLockRegistryTest {

    @Test
    void serializesWorkForSameIdempotencyKey() throws Exception {
        var registry = new LocalIdempotencyLockRegistry();
        var currentConcurrency = new AtomicInteger();
        var maxConcurrency = new AtomicInteger();
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(4);
        var executor = Executors.newFixedThreadPool(4);

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    registry.executeWithLock("same-key", () -> {
                        int running = currentConcurrency.incrementAndGet();
                        maxConcurrency.accumulateAndGet(running, Math::max);
                        sleep(50);
                        currentConcurrency.decrementAndGet();
                        return null;
                    });
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(maxConcurrency).hasValue(1);
        assertThat(registry.activeLockCount()).isZero();
    }

    @Test
    void allowsWorkForDifferentIdempotencyKeysToRunConcurrently() throws Exception {
        var registry = new LocalIdempotencyLockRegistry();
        var bothInside = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var done = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> runBlockingWork(registry, "key-1", bothInside, release, done));
        executor.submit(() -> runBlockingWork(registry, "key-2", bothInside, release, done));

        assertThat(bothInside.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(registry.activeLockCount()).isZero();
    }

    private void runBlockingWork(
            LocalIdempotencyLockRegistry registry,
            String key,
            CountDownLatch bothInside,
            CountDownLatch release,
            CountDownLatch done
    ) {
        try {
            registry.executeWithLock(key, () -> {
                bothInside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        } finally {
            done.countDown();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
