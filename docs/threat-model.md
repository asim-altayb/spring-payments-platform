# Threat model

## Protected assets

- Account balances and the immutable journal
- Idempotency identities and transfer status
- JWT signing material and webhook secret
- Personally identifiable partner metadata (not modeled in this reference)

## Primary threats and controls

| Threat | Control | Residual work for production |
|---|---|---|
| Replayed transfer request | Client-scoped idempotency key and unique constraint | Expire/archive old keys under a documented retention policy |
| Concurrent lost update | Optimistic account version | Load and soak tests for retry policy under real contention |
| Unauthorized money movement | JWT authentication and `transfers:write` scope | External issuer, asymmetric keys, short TTL, revocation policy |
| Ledger tampering | No mutation API plus database trigger | Separate DB role, WORM export, reconciliation and alerts |
| Forged webhook | HMAC-SHA256 signature | Timestamped envelope, replay window, secret rotation/versioning |
| Secret disclosure | Environment injection; no committed secret | Managed secret store and automated rotation |
| Event loss during crash | Outbox is committed with transfer | Destination acknowledgements and dead-letter workflow |
| Sensitive log leakage | Payload contains identifiers only | Central redaction policy and restricted log access |

## Trust boundaries

The public API, database, identity provider, and external event destination are separate trust zones. TLS is assumed between all zones. The local defaults are for developer convenience and must not be deployed unchanged.

