# Architecture

## Invariants

1. A completed transfer changes two accounts and creates exactly two ledger entries in one database transaction.
2. Debit and credit amounts and currencies are identical.
3. An account balance cannot become negative.
4. A client/idempotency-key pair identifies at most one transfer.
5. Ledger rows are append-only.
6. An integration event cannot exist without its committed transfer.

The application checks these rules for useful errors; PostgreSQL constraints remain the final authority when processes race or application code changes.

## Transaction boundary

`TransferService.execute` loads both versioned aggregates, validates currency and funds, applies the balance changes, persists the transfer, writes debit/credit journal rows, and appends an outbox event. Any failure rolls back the complete unit.

Optimistic versions detect concurrent balance updates without holding long-lived application locks. The API tells a caller to retry using the same idempotency key, turning an uncertain result into a safe replay.

## Delivery boundary

The publisher claims small batches with PostgreSQL `FOR UPDATE SKIP LOCKED`. Multiple replicas can work without publishing the same claimed row. Events remain pending after a failed attempt and are bounded by an attempt limit for operational inspection.

The transport itself is an adapter boundary. This repository logs HMAC-signed payloads; HTTP, Kafka, or a bank gateway can be introduced without changing the money transaction.

## Data ownership

Flyway owns schema changes. Hibernate runs in `validate` mode so deployment fails early if code and schema disagree. No application endpoint can edit ledger rows, and a database trigger rejects update/delete even from an accidental repository method.

