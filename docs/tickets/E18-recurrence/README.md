# SELLO-E18 — Recurring payments, incomes and reservations

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** Let a person schedule recurring payments and incomes that Sello records on their dates, reserving payments still to come.
- **Exit:** Schedules can be created, paused, ended and changed; due occurrences are recorded on opening with a notice; Recibo's headline is after reservations with Sin margen and Revisar; income history shows Plan del mes; backups carry schedules.

## Decisions

**Owner direction (2026-10-23):** decisions in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md): monthly, twice a month and
every N days or weeks; recorded automatically when Sello opens, catching up every
missed occurrence on its own date; changes affect future occurrences only; pause,
resume and an optional end; no notifications or background work. Ships with Metas and
Inversiones in one release. [ADR 0012](../../decisions/0012-budget-guidance-contract.md)
and ARCHITECTURE §8 own reservations and recurrence safety.
