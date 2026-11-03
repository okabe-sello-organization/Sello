# SELLO-162 — A damaged backup or FinanceTracker file is refused, never breaks the app

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
Validation refuses every value the app couldn't read back after a restore, and the
FinanceTracker translator counts an unreadable rate as skipped instead of crashing.

### Acceptance criteria
- A backup with a goal event, expense, income, schedule, investment movement or rate
  at `sequence` 9223372036854775807 is refused as malformed and nothing changes. The
  validator accepts exactly what the mappers and counters accept.
- A FinanceTracker file with `"rate": 1e-999999999` (or a huge positive exponent)
  finishes translating with that record counted as skipped (out of range); no crash,
  no hang.
- JVM tests with synthetic files.

### Notes
Audit findings: `BackupValidation.kt:634` (goals; investments refuse it at `:789`),
`RoomRestores.kt:480`, `LegacyTranslator.kt:910-911` (`setScale` on a huge scale).
ADR 0019. Review: adversarial (backup).
