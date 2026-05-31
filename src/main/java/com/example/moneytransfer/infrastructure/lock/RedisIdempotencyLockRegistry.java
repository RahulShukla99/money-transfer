package com.example.moneytransfer.infrastructure.lock;

import com.example.moneytransfer.application.service.IdempotencyLockRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "money-transfer.lock", name = "type", havingValue = "redis")
public class RedisIdempotencyLockRegistry implements IdempotencyLockRegistry {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyLockRegistry.class);
    private static final String LOCK_KEY_PREFIX = "money-transfer:idempotency-lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final Duration waitTimeout;
    private final Duration retryDelay;

    public RedisIdempotencyLockRegistry(
            StringRedisTemplate redisTemplate,
            @Value("${money-transfer.lock.redis.ttl:30s}") Duration ttl,
            @Value("${money-transfer.lock.redis.wait-timeout:2s}") Duration waitTimeout,
            @Value("${money-transfer.lock.redis.retry-delay:50ms}") Duration retryDelay
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.waitTimeout = waitTimeout;
        this.retryDelay = retryDelay;
    }

    @Override
    public <T> T executeWithLock(String idempotencyKey, Supplier<T> action) {
        String lockKey = LOCK_KEY_PREFIX + idempotencyKey;
        String token = UUID.randomUUID().toString();
        acquire(lockKey, token);
        try {
            return action.get();
        } finally {
            release(lockKey, token);
        }
    }

    private void acquire(String lockKey, String token) {
        long deadline = System.nanoTime() + waitTimeout.toNanos();

        while (System.nanoTime() < deadline) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Redis idempotency lock acquired: key={}", lockKey);
                return;
            }
            sleepBeforeRetry();
        }

        throw new CannotAcquireLockException("Could not acquire Redis idempotency lock: " + lockKey);
    }

    private void release(String lockKey, String token) {
        Long released = redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        if (Long.valueOf(1L).equals(released)) {
            log.debug("Redis idempotency lock released: key={}", lockKey);
        } else {
            log.warn("Redis idempotency lock was not released because token did not match or key expired: key={}", lockKey);
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("Interrupted while waiting for Redis idempotency lock", ex);
        }
    }
}
