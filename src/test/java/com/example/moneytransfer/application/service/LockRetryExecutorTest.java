package com.example.moneytransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

class LockRetryExecutorTest {

    @Test
    void retriesLockFailureAndReturnsSuccessfulResult() {
        var attempts = new AtomicInteger();
        var executor = new LockRetryExecutor(3, Duration.ZERO, ignored -> {
        });

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("row is locked");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void stopsAfterMaxAttemptsForLockFailure() {
        var attempts = new AtomicInteger();
        var executor = new LockRetryExecutor(3, Duration.ZERO, ignored -> {
        });

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new PessimisticLockingFailureException("lock timeout");
        })).isInstanceOf(PessimisticLockingFailureException.class);

        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryNonLockFailures() {
        var attempts = new AtomicInteger();
        var executor = new LockRetryExecutor(3, Duration.ZERO, ignored -> {
        });

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("business validation failed");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts).hasValue(1);
    }
}
