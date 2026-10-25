# SELLO-099 — Carry schedules through backup, restore and erase

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-098, SELLO-004
- **Gate:** G2

### Outcome
Backups include schedules; restore and erase handle them without replaying occurrences twice.

### Deliverables
Backup format version 3 (version 2 holds goals, SELLO-088): schedules and the origin of each recorded occurrence, with docs, restore and reset updates.

### Acceptance criteria
- Restored schedules do not re-record occurrences already in the backup.
- Versions 1 and 2 still restore, as having no schedules.

### Tests
Codec and round-trip tests; G2.

### Working checkpoint
A backup with schedules restores and catches up correctly.

### Context and starting points
`docs/data/backup-format.md`.

### Implementation plan
1. Format.
2. Restore and reset.
3. Tests.

### Concrete cases and pitfalls
- Restore resets catch-up state to the backup's recorded occurrences.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-099 --retain`.

### Execution progress

- **Owner decisions, 2026-10-24:** the counts say "pagos e ingresos fijos" ("1 pago o
  ingreso fijo"); and, added to this ticket's scope, resuming a payment whose category is
  archived is refused (`CategoryArchived`). That gap was found while writing this
  ticket's validation: a resumed payment into an archived category makes catch-up fail
  for every schedule.
- **Format version 3:** `financial.schedules` (entry, rule, start, end, creation order,
  `handled` checkpoint, pauses, times) and each expense's and income's `origin`; versions
  1 and 2 read as having neither. Validation holds a file to the stored rules, refuses an
  origin past its schedule's checkpoint (catch-up would record it again) and a running
  payment into an archived category. Export reads schedules in its transaction; restore
  inserts them with their checkpoints, so catch-up goes on after what the backup holds.
  Backup, restore and erase show the count; the last backup's record keeps it.
- **Tests:** host `ScheduleBackupTest` (9) with `valid-schedules-v3.json`, version 3 forms of
  G01 and the goal file; device `ScheduleBackupRestoreTest` (3: restore then catch-up,
  replace not merge, erase counts and removes), the resume refusal in
  `ScheduleCommandContractTest`, two `ResetScreenTest` counts. Red: the resume test
  failed before the fix. Nine deliberate breaks of codec, validation, export, restore and
  erase count each fail a test.
