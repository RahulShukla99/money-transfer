create table if not exists accounts (
    id uuid primary key,
    balance numeric(19, 2) not null,
    currency varchar(3) not null,
    version bigint not null
);

create table if not exists transaction_records (
    id uuid primary key,
    from_account_id uuid not null,
    to_account_id uuid not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    idempotency_key varchar(255) not null,
    status varchar(20) not null,
    message varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_transaction_idempotency_key unique (idempotency_key)
);

create index if not exists idx_transaction_records_from_account
    on transaction_records (from_account_id);

create index if not exists idx_transaction_records_to_account
    on transaction_records (to_account_id);
