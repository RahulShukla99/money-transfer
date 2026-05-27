# Money Transfer Service

Spring Boot service for transferring money between accounts with DDD-style layering, PostgreSQL persistence, idempotency, concurrency safety, local duplicate-request serialization, retry handling for database lock failures, and transaction audit records.

## Features

- Validates source account balance before transfer.
- Debits source account and credits destination account atomically.
- Prevents duplicate processing with a client-provided `idempotencyKey`.
- Uses database pessimistic row locks for concurrent account updates.
- Locks accounts in deterministic UUID order to reduce deadlock risk.
- Uses local per-idempotency-key `ReentrantLock` serialization inside one JVM.
- Retries transient database lock failures outside the transfer method.
- Stores transaction records with `PROCESSING`, `SUCCESS`, and `FAILED` status.
- Exposes a REST API for transfer requests.

## Project Location

```text
C:\Users\rahul\IdeaProjects\transfer-fromaccountid-toaccountid-amount-idempotencykey-design
```

## Tech Stack

- Java 21
- Spring Boot 3.3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- H2 for tests
- Maven
- JUnit 5

## Package Structure

```text
com.example.moneytransfer
  application
    dto                 Use-case input/output DTOs
    service             Transfer orchestration, local locking, retry executor
  domain
    exception           Business exceptions
    model               Account, Money, TransactionRecord, TransactionStatus
  infrastructure
    persistence         Spring Data JPA repositories
  interfaces
    rest                REST controller, request model, API error handling
```

## Database Setup

The app currently connects to a PostgreSQL database named `transfer_app`.

Connection settings in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/transfer_app
    username: postgres
    password: 1
```

Create the database once in pgAdmin or psql:

```sql
CREATE DATABASE transfer_app;
```

On application startup:

1. `src/main/resources/schema.sql` creates the tables if they do not exist.
2. `src/main/resources/data.sql` inserts sample accounts.

Verify in pgAdmin:

```sql
select * from accounts;
select * from transaction_records;
```

Sample seeded accounts:

```text
11111111-1111-1111-1111-111111111111 -> 1000.00 USD
22222222-2222-2222-2222-222222222222 -> 100.00 USD
```

## Run From IntelliJ

1. Open the project folder in IntelliJ IDEA.
2. Ensure Project SDK is Java 21.
3. Let IntelliJ import Maven dependencies from `pom.xml`.
4. Create PostgreSQL database `transfer_app`.
5. Run `MoneyTransferApplication`.

The app starts at:

```text
http://localhost:8080
```

## Run From Terminal

```powershell
mvn spring-boot:run
```

Run tests:

```powershell
mvn test
```

## Transfer API

Full URL:

```text
POST http://localhost:8080/api/transfers
```

Headers:

```text
Content-Type: application/json
```

Sample request:

```json
{
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 25.00,
  "idempotencyKey": "postman-transfer-001"
}
```

Sample success response:

```json
{
  "transactionId": "generated-uuid",
  "status": "SUCCESS",
  "message": "Transfer completed",
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 25.00,
  "currency": "USD",
  "idempotencyKey": "postman-transfer-001"
}
```

## Postman

Import the collection:

```text
postman/Money Transfer Service.postman_collection.json
```

Or create a request manually using:

```text
POST http://localhost:8080/api/transfers
```

## How A Transfer Works

```text
HTTP JSON request
  -> TransferRequest
  -> TransferCommand
  -> TransferService
  -> reserve/find idempotency transaction record
  -> lock both account rows in deterministic order
  -> debit source account
  -> credit destination account
  -> mark transaction record SUCCESS
  -> return TransferResult
```

## Idempotency

The client creates and sends an `idempotencyKey`.

The server stores it in:

```text
transaction_records.idempotency_key
```

The database enforces uniqueness:

```sql
constraint uk_transaction_idempotency_key unique (idempotency_key)
```

If the same request is retried with the same key, the service returns the existing transaction result instead of moving money again.

If the same key is reused with different transfer details, the service rejects it.

## Concurrency Safety

The service uses two layers of protection:

1. Local JVM lock by `idempotencyKey`
2. PostgreSQL row locks on account records

The local lock prevents duplicate same-key work inside one running app instance.

The database lock protects correctness across all app instances:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

Hibernate turns this into SQL similar to:

```sql
select *
from accounts
where id = ?
for update;
```

## Deadlock Reduction

Transfers may involve the same two accounts in opposite directions:

```text
A -> B
B -> A
```

To reduce deadlock risk, the service always sorts account IDs and locks them in the same order:

```text
smaller UUID first
larger UUID second
```

So both requests try to lock accounts in the same order.

## Retry Behavior

`LockRetryExecutor` retries transient lock-related failures outside the main transfer method.

Retried exceptions:

- `CannotAcquireLockException`
- `PessimisticLockingFailureException`
- `QueryTimeoutException`

Default policy:

```text
max attempts: 3
backoff: 100ms, then 200ms
```

Non-lock exceptions, such as validation failures or insufficient funds, are not retried.

## Transaction Boundaries

The service uses `TransactionTemplate`.

Main transfer transaction:

```text
lock accounts
debit source
credit destination
mark SUCCESS
```

Separate `REQUIRES_NEW` transactions:

```text
create PROCESSING transaction record
mark FAILED transaction record
```

This ensures failure audit records survive even when the money movement transaction rolls back.

## Important Files

```text
src/main/java/com/example/moneytransfer/interfaces/rest/TransferController.java
src/main/java/com/example/moneytransfer/interfaces/rest/TransferRequest.java
src/main/java/com/example/moneytransfer/application/service/TransferService.java
src/main/java/com/example/moneytransfer/application/service/LocalIdempotencyLockRegistry.java
src/main/java/com/example/moneytransfer/application/service/LockRetryExecutor.java
src/main/java/com/example/moneytransfer/domain/model/Account.java
src/main/java/com/example/moneytransfer/domain/model/TransactionRecord.java
src/main/resources/schema.sql
src/main/resources/data.sql
```

## Tests

The test suite covers:

- successful transfer
- insufficient funds
- idempotent replay
- idempotency key conflict
- concurrent same-key requests
- local lock serialization
- lock retry behavior
- REST endpoint request/response

Run:

```powershell
mvn test
```

## Additional Design Documentation

See:

```text
docs/architecture.md
```
