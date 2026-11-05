# 0025 — Catch-up stamps what it records after the notice's mark

**Owner:** project owner (user) · **Date:** 2026-11-04
**Status:** orchestrated E29 decision (issue #340); refines the creation time of
[ADR 0004](0004-category-latest-expense-order.md) for entries a schedule records.

## Context

The catch-up notice lists the entries with an origin created after its mark: the latest
stamp Listo showed, or the moment of the last restore. Both are audit-clock (wall-clock)
instants. When the device clock moved back after a Listo, what catch-up recorded next was
stamped before the mark and never listed, although it was recorded
([recurrence examples](../testing/recurrence-examples.md) rule 14, RE41).

## Decision

- Each catch-up batch is stamped at the audit clock's time, unless that is not after the
  latest moment the notice can be marked seen up to: the stored mark, the newest expense
  or income with an origin, and the last catch-up and restore receipts. Then it is
  stamped one millisecond after that moment. The batch's receipt gets the same stamp.
- The mark is read outside the transaction and never blocks recording: unreadable, it
  adds nothing (each read starts afresh), and the stored stamps and receipts still hold
  every mark, except one up to entries a restore brought that were deleted since.
- A moment that cannot be followed (`Long.MAX_VALUE` ms) refuses the batch with nothing
  written, as other exhausted counters do.
- No schema, backup-format or preference change: `createdAt` stays epoch milliseconds.

## Alternatives

Mark seen by each table's `sequence`: kept copies of deleted rows are discarded when
storage opens, so a sequence can be handed out again at or below the mark and hide an
entry; it would also need a new preference shape. Mark seen by the profile revision or
the catch-up receipt: no entry refers to either, so it needs a schema change.

## Consequences

- An entry a schedule recorded can be stamped later than the device clock while the
  clock is behind. Último gasto agregado (D06, ADR 0004) then ranks its category as the
  newest addition, ahead of an expense added by hand at the clock's time meanwhile.
- Accepted residual: a clock once set forward (a Listo, a catch-up or a restore at that
  future moment) keeps every later batch after it until real time passes it, so
  automatic entries keep ranking newest in Último gasto agregado until then.
- With the clock never set back, stamps are the clock's time, as before.

## Verification

Real-Room tests in `ScheduleRecordReadsTest` and `ScheduleBackupRestoreTest`: a clock set
back after Listo, after the seen entries were deleted and after a restore still lists what
catch-up records; a mark held only in preferences, readable or not; the clock's time
kept when not set back; and a refused batch at the largest moment (issue #340).
