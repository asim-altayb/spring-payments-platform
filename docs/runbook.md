# Operations runbook

## Health and telemetry

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Prometheus: `GET /actuator/prometheus` with `ops:read`
- Business counter: `payments_transfers_completed_total`

## Outbox backlog

Alert when the oldest unpublished event exceeds the delivery SLO or when events reach eight attempts.

```sql
select count(*) as pending, min(occurred_at) as oldest
from outbox_events where published_at is null;

select id, aggregate_id, attempts, occurred_at
from outbox_events where published_at is null and attempts >= 8
order by occurred_at;
```

Do not mark an event published merely to clear an alert. Correct the destination or payload problem, verify the signature contract, then replay through a controlled operational command.

## Balance reconciliation

Continuously compare each materialized balance with its opening position plus credits minus debits. Any mismatch is a severity-one integrity incident: stop writes for the affected account, preserve logs and database state, and investigate before repair.

## Safe deployment

1. Back up and rehearse migration restore.
2. Apply backward-compatible Flyway migrations before traffic reaches new code.
3. Verify readiness and error-rate metrics on a canary.
4. Confirm outbox age and database lock waits remain within SLO.
5. Roll back application code independently; never roll back an applied destructive schema migration.

