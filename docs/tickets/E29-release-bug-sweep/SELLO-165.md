# SELLO-165 — Room 9: indexes for long histories and accent-blind search

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Reads that grow with history stay fast, and searching expenses ignores accents and case
for every letter.

### Acceptance criteria
- First, on a real device: a note "ÑAME ÁREA" searched as "ñame" and as "área". If
  Android's SQLite already finds it, the search key is left out and the pull request
  says so.
- One migration 8 → 9 adds an index for the "recorded since" read, an index on
  receipts by kind and time, and (if needed) a stored search key filled for existing
  rows. Exported schema 9, and a real-Room test that version-8 data survives.
- The "recorded since" read is bounded when nothing has been seen yet.
- The backup format does not change; a backup made before restores, and the search key
  is rebuilt on restore.
- No destructive fallback.

### Notes
Owner decision: both parts, one schema step, only here. Audit findings:
`ExpenseDao.kt:13, 25, 73-77`, `IncomeDao.kt:14-18`, `RoomScheduleRecordReads.kt:26, 37, 56`,
`OperationReceiptDao.kt:18-19`, `OperationReceiptEntity.kt:16`, `RoomRestores.kt:369`.
ARCHITECTURE §5 and §7. Review: adversarial (schema).
