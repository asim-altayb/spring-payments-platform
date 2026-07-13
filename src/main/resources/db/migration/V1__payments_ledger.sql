create table accounts (
    id uuid primary key,
    external_reference varchar(120) not null unique,
    currency varchar(3) not null check (currency = upper(currency)),
    balance numeric(19,4) not null check (balance >= 0),
    version bigint not null default 0
);

create table transfers (
    id uuid primary key,
    client_id varchar(120) not null,
    idempotency_key varchar(200) not null,
    source_account_id uuid not null references accounts(id),
    destination_account_id uuid not null references accounts(id),
    amount numeric(19,4) not null check (amount > 0),
    currency varchar(3) not null,
    status varchar(24) not null,
    created_at timestamptz not null,
    constraint different_accounts check (source_account_id <> destination_account_id),
    constraint unique_idempotent_request unique (client_id, idempotency_key)
);

create table ledger_entries (
    id uuid primary key,
    transfer_id uuid not null references transfers(id),
    account_id uuid not null references accounts(id),
    direction varchar(8) not null check (direction in ('DEBIT', 'CREDIT')),
    amount numeric(19,4) not null check (amount > 0),
    currency varchar(3) not null,
    created_at timestamptz not null
);

create unique index one_entry_per_side on ledger_entries (transfer_id, direction);
create index ledger_account_timeline on ledger_entries (account_id, created_at desc);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    event_type varchar(120) not null,
    payload jsonb not null,
    occurred_at timestamptz not null,
    published_at timestamptz,
    attempts integer not null default 0 check (attempts >= 0)
);

create index pending_outbox_events on outbox_events (occurred_at)
    where published_at is null;

-- The immutable journal is append-only at the database boundary.
create function reject_ledger_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'ledger entries are immutable';
end;
$$;

create trigger immutable_ledger_update before update or delete on ledger_entries
    for each row execute function reject_ledger_mutation();

