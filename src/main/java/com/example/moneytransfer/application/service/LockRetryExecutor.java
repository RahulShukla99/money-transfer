package com.example.moneytransfer.application.service;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

@Component
public class LockRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(LockRetryExecutor.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(100);

    private final int maxAttempts;
    private final Duration backoff;
    private final Sleeper sleeper;

    public LockRetryExecutor() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF, Thread::sleep);
    }

    LockRetryExecutor(int maxAttempts, Duration backoff, Sleeper sleeper) {
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
        this.sleeper = sleeper;
    }

    public <T> T execute(Supplier<T> action) {
        int attempt = 1;
        while (true) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!isRetriableLockFailure(ex) || attempt >= maxAttempts) {
                    throw ex;
                }

                log.warn(
                        "Retrying transfer after lock failure: attempt={}, maxAttempts={}, reason={}",
                        attempt,
                        maxAttempts,
                        ex.getMessage()
                );
                sleepBeforeRetry(attempt);
                attempt++;
            }
        }
    }

    private boolean isRetriableLockFailure(RuntimeException ex) {
        return ex instanceof CannotAcquireLockException
                || ex instanceof PessimisticLockingFailureException
                || ex instanceof QueryTimeoutException;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            sleeper.sleep(backoff.multipliedBy(attempt).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry lock failure", ex);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
