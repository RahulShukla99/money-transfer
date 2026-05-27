package com.example.moneytransfer.application.service;

import java.util.function.Supplier;

public interface IdempotencyLockRegistry {

    <T> T executeWithLock(String idempotencyKey, Supplier<T> action);
}
