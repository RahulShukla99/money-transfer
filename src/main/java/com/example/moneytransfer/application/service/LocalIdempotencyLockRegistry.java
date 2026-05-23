package com.example.moneytransfer.application.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class LocalIdempotencyLockRegistry {

    private final ConcurrentHashMap<String, LockHolder> locks = new ConcurrentHashMap<>();

    public <T> T executeWithLock(String idempotencyKey, Supplier<T> action) {
        LockHolder holder = locks.compute(idempotencyKey, (key, existing) -> {
            if (existing == null) {
                return new LockHolder();
            }
            existing.incrementUsers();
            return existing;
        });

        holder.lock();
        try {
            return action.get();
        } finally {
            holder.unlock();
            locks.computeIfPresent(idempotencyKey, (key, existing) -> existing.decrementUsers() == 0 ? null : existing);
        }
    }

    int activeLockCount() {
        return locks.size();
    }

    private static class LockHolder {

        private final ReentrantLock lock = new ReentrantLock(true);
        private int users = 1;

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }

        void incrementUsers() {
            users++;
        }

        int decrementUsers() {
            return --users;
        }
    }
}
