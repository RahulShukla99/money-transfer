# Architecture And Design Notes

## Goal

The service implements:

```java
transfer(fromAccountId, toAccountId, amount, idempotencyKey)
```

It validates balance, transfers money atomically, prevents duplicate processing, handles concurrency, stores transaction records, and returns clear responses.

## Layer Responsibilities

| Layer | Responsibility |
|---|---|
| `interfaces.rest` | HTTP request/response handling, validation annotations, exception-to-HTTP mapping |
| `interfaces.stream` | Kafka message consumption, in-memory stream queueing, and stream-message-to-command mapping |
| `application` | Use-case orchestration, transaction boundaries, idempotency lock abstraction, retry policy |
| `domain` | Business objects and rules: account debit/credit, money validation, transaction status |
| `infrastructure.persistence` | Database repository interfaces and JPA locking queries |
| `infrastructure.lock` | Redis-backed lock adapter for distributed idempotency locking |

## Request Flow

```text
HTTP Client or Kafka Topic
  -> TransferController or TransferStreamConsumer
  -> TransferRequest or TransferStreamMessage
  -> LinkedBlockingQueue for stream messages
  -> TransferCommand
  -> TransferService
  -> IdempotencyLockRegistry
  -> transaction_records idempotency check/reservation
  -> LockRetryExecutor
  -> account row locks
  -> debit/credit
  -> transaction_records SUCCESS/FAILED
  -> TransferResult
```

## Why Kafka Is An Interface Adapter

Kafka is another way for the outside world to start the same transfer use case.

The stream consumer belongs in:

```text
interfaces.stream
```

because it accepts external input, validates/maps it, and then calls the application service. It should not contain debit, credit, idempotency, or database transaction logic.

The important design point:

```text
REST request -> TransferCommand -> TransferService
Kafka message -> LinkedBlockingQueue -> TransferCommand -> TransferService
```

Both entry points reuse the same use case, so concurrency, idempotency, account locking, retry, and audit behavior stay consistent.

## Why There Is A LinkedBlockingQueue

The stream adapter includes a bounded `LinkedBlockingQueue`.

Its purpose is local buffering and backpressure inside one app instance:

```text
KafkaListener thread -> LinkedBlockingQueue -> worker thread -> TransferService
```

The queue is not the durable source of truth. Kafka is still the durable stream, and PostgreSQL remains the correctness boundary for idempotency and money movement.

The worker acknowledges the Kafka message only after the transfer succeeds. If processing fails, the message is not acknowledged, and Kafka can redeliver it.

## Why Redis Is Infrastructure

The application service should not know Redis details.

It depends on:

```text
IdempotencyLockRegistry
```

The local and Redis implementations are replaceable adapters:

```text
LocalIdempotencyLockRegistry
RedisIdempotencyLockRegistry
```

This keeps the transfer use case independent from the locking technology.

## Idempotency Locking

The lock prevents duplicate same-key work before the service reaches the database transfer logic.

Local mode:

```text
same idempotencyKey -> one at a time in this JVM
```

Redis mode:

```text
same idempotencyKey -> one at a time across app instances sharing Redis
```

Redis uses:

```text
SET key token NX PX ttl
```

and unlocks with a Lua script that deletes the key only if the token matches.

## Database Idempotency

The database remains the final correctness boundary:

```sql
constraint uk_transaction_idempotency_key unique (idempotency_key)
```

Even if Redis is unavailable or two app instances race, PostgreSQL prevents duplicate transaction rows for the same key.

## Account Locking

The repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findByIdForUpdate(UUID id);
```

maps to SQL similar to:

```sql
select *
from accounts
where id = ?
for update;
```

This blocks concurrent writes to the same account balance until the transaction commits or rolls back.

## Deadlock Reduction

The service sorts account IDs before locking:

```java
List<UUID> lockOrder = List.of(command.fromAccountId(), command.toAccountId())
        .stream()
        .sorted(Comparator.naturalOrder())
        .toList();
```

So `A -> B` and `B -> A` both lock accounts in the same order.

## Retry Design

`LockRetryExecutor` retries transient lock failures:

- `CannotAcquireLockException`
- `PessimisticLockingFailureException`
- `QueryTimeoutException`

The retry policy is outside `executeTransfer(...)`, so business logic stays separate from retry mechanics.

## Failure Scenarios

| Scenario | Result |
|---|---|
| Source account has insufficient funds | Transfer rolls back, transaction record marked `FAILED` |
| Destination account does not exist | Transfer rolls back, transaction record marked `FAILED` |
| Same idempotency key retried with same payload | Existing result returned |
| Same idempotency key retried with different payload | Request rejected |
| Database lock cannot be acquired | Retried by `LockRetryExecutor` |
| Debit succeeds but credit fails | Entire DB transaction rolls back |

## Scaling Notes

For one app instance, local locking is enough as an optimization.

For multiple app instances, Redis locking reduces duplicate same-key work across instances.

For correctness, this service relies on PostgreSQL:

- unique idempotency key constraint
- pessimistic row locks
- database transactions

For distributed microservices, this would usually evolve into:

- saga orchestration
- transactional outbox
- idempotent debit/credit operations per service
- eventual consistency and compensating actions
