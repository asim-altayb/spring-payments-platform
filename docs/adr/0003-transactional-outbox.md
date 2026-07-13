# ADR 0003: Transactional outbox

Status: accepted

Directly publishing during a database transaction can lose an event or announce a transfer that later rolls back. The service writes an outbox row beside the transfer, then an independent worker claims committed rows. Delivery is at-least-once; consumers must deduplicate by event ID.

