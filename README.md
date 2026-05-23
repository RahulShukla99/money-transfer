# Money Transfer Service

Spring Boot service implementing:

- balance validation
- atomic debit and credit
- idempotency via a unique `idempotency_key`
- concurrent request safety with pessimistic database locks
- transaction records
- clear success/failure API responses

## Architecture

The project follows a DDD-style package layout:

- `domain`: aggregates, value objects, enums, and domain exceptions
- `application`: transfer use case and DTOs
- `infrastructure.persistence`: JPA repositories
- `interfaces.rest`: REST controller and error mapping

## Run PostgreSQL and pgAdmin

```powershell
docker compose up -d
```

PostgreSQL:

- Host: `localhost`
- Port: `5432`
- Database: `transfer_app`
- User: `postgres`
- Password: `1`

pgAdmin:

- URL: `http://localhost:5050`
- Email: `admin@example.com`
- Password: `admin`

The app connects to the `transfer_app` database, creates tables from `src/main/resources/schema.sql`, and seeds two sample accounts from `src/main/resources/data.sql` on startup:

```sql
select * from accounts;
```

If you want to seed manually instead, run this in pgAdmin Query Tool after the app has created the tables:

```sql
insert into accounts (id, balance, currency, version)
values
('11111111-1111-1111-1111-111111111111', 1000.00, 'USD', 0),
('22222222-2222-2222-2222-222222222222', 100.00, 'USD', 0)
on conflict (id) do nothing;
```

## Run the service

```powershell
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

## Seed accounts

```sql
insert into accounts (id, balance, currency, version)
values
('11111111-1111-1111-1111-111111111111', 1000.00, 'USD', 0),
('22222222-2222-2222-2222-222222222222', 100.00, 'USD', 0);
```

## Transfer API

```http
POST /api/transfers
Content-Type: application/json

{
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 25.00,
  "idempotencyKey": "client-request-123"
}
```

Successful response:

```json
{
  "transactionId": "generated-uuid",
  "status": "SUCCESS",
  "message": "Transfer completed",
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 25.00,
  "idempotencyKey": "client-request-123"
}
```

## Design Notes

- Race conditions are avoided by wrapping the transfer in one database transaction and locking both account rows with `PESSIMISTIC_WRITE`.
- Deadlocks are reduced by locking accounts in deterministic UUID order before applying debit and credit.
- Idempotency is enforced by a unique database constraint on `transaction_records.idempotency_key`.
- Debit and credit are atomic because both happen in the same database transaction. If credit fails, the whole transaction rolls back and the transaction record is marked failed in a separate transaction.
- In microservices, replace the single database transaction with a saga/outbox pattern and make debit/credit operations idempotent in each service.
