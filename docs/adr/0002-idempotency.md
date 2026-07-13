# ADR 0002: Client-scoped idempotency keys

Status: accepted

Network timeouts make a successful payment look failed to a caller. Each mutating request supplies an idempotency key scoped to its client. A unique constraint provides the race-safe authority, and completed replays return the original transfer. Keys must be stable for one logical operation and never reused with different payloads.

