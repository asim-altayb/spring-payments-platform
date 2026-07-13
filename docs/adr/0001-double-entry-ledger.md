# ADR 0001: Double-entry journal with materialized balances

Status: accepted

Every transfer writes equal debit and credit entries and updates account balances in one transaction. Materialized balances make authorization and reads fast; the journal provides an immutable audit source and reconciliation path. The cost is duplicated state, managed through constraints, tests, and continuous reconciliation.

