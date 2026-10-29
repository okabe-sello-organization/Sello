# SELLO-088 — Carry goals through backup, restore and erase

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-087, SELLO-004
- **Gate:** G2

### Outcome
Backups include goals and movements; restore and erase-all handle them.

### Deliverables
Backup format version 2 with goals, reading version 1 unchanged; restore and reset updated; format doc.

### Acceptance criteria
- Version 1 files still restore; version 2 round-trips goals exactly.
- Erase-all removes goals; restore replaces them, never merges.

### Tests
Codec and real-Room round-trip tests, malformed-file tests; G2.

### Working checkpoint
A backup with goals restores on a fresh install with the same figures.

### Context and starting points
`docs/data/backup-format.md`; SELLO-028–031.

### Implementation plan
1. Format v2 and docs.
2. Export, validation, restore, reset.
3. Tests.

### Concrete cases and pitfalls
- Every new key is required; unknown versions are refused.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-088 --retain`.

### Execution progress

- **Owner decisions, 2026-10-24:** every backup is version 2, even with no goals (0.1.0
  cannot read it); the counts before restoring and erasing, after restoring and under
  the last backup add Metas only when either side has goals.
- Format version 2 (`goals`, `featuredGoal`) written, versions 1 and 2 read; a goal's
  history is validated with `GoalPolicy`, and a target carries no identifier in the
  file. Export, restore (replacing, never merging) and erase-all include the goal
  tables; `BackupSummary` counts goals, and the stored last backup keeps the count
  (absent in a 0.1.0 record, where it was 0).
