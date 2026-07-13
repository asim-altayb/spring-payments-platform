# ADR 0004: Optimistic account concurrency

Status: accepted

Accounts carry a version checked at commit. This prevents silent lost updates while avoiding long application-held locks. Contention becomes an explicit retryable failure, and the same idempotency key makes that retry safe. Hot accounts may later require partitioned serialization or a database lock strategy based on measured contention.
