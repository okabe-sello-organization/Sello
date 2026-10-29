# SELLO-034 — Prove integrated data retention and recovery failure safety

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-030, SELLO-031, SELLO-032, SELLO-027, SELLO-004
- **Gate:** G2

### Outcome
Test the complete release data lifecycle, including interrupted operations, against real persistence.

### Deliverables
Cross-version schema migration retention corpus for every implemented version,
backup fixtures, restore/reset cancellation matrix and executable synthetic recovery
scenarios. Include expenses, income, archived categories, period limits, ordering
and preferences. Document corruption/unavailable-storage behavior.

### Acceptance criteria
- Migration upgrades preserve exact financial identities/history; no destructive
  fallback. With only v1, verify fresh/reopen plus migration harness, not invent a v0 upgrade.
- Invalid backup, overflow, interrupted replacement and failed preferences have
  truthful outcomes; receipts/generation prevent duplicate or stale recovery.
- Temporal replay and backup/restored charts agree with independent expected totals.
  Re-running reset/import does not resurrect discarded payloads or sandbox state.

### Tests
Run real-Room migration/transaction/device corpus with controlled failure points,
process interruption and reopen; execute full sandbox regression inventory; G2.

### Working checkpoint
Every persisted MVP capability survives supported upgrade/backup/restore paths,
or fails safely with a recoverable documented state. Retain command/device evidence.

### Context and starting points
Schema, backup and cancellation tests can each pass while their integrated recovery
path fails. Inputs are actual migration/schema exports, backup formats, reset/restore
records and scenario corpus. Read architecture §§5/7/10 and the guide's Room migration/
failure references; do not invent compatibility with the old app database.

### Implementation plan
1. Inventory every schema and portable-format version actually shipped/implemented;
   define supported upgrade/restore pairs in `docs/testing/recovery-matrix.md`.
2. Prepare independently asserted synthetic prior-state fixtures with archived
   categories, month limits, expense/income order, profile zone and preferences.
3. Execute real-file migrations/reopen and backup/restore under failures before/inside/
   after financial commit, then before/after preferences; verify rows and recovery receipts.
4. Test stale submit/undo/import/reset races across generation change and caller death,
   ensuring pending settings resume without repeating financial replacement.
5. Run fresh/resumed temporal corpus and cross-check exact chart/list/backup values;
   fix gaps with regression tests in owning modules, not unchecked recovery shortcuts.

### Concrete cases and pitfalls
- With only schema v1, fresh/reopen is legitimate; a fake v0 migration is not evidence.
- A valid restore with preferences failure is committed+pending, not rollback or
  complete success. Backup-corruption rejection leaves every store unchanged.
- Old record IDs after restore cannot make stale undo eligible. Retained reset
  metadata cannot contain erased payloads; real corrupt data never becomes an empty ledger.

### Verification recipe
Run data/app connected migration/recovery suites and TemporalScenarioCorpusTest,
then G2. Record each failure point, before/after row/receipt/generation state and
supported version pair. Archive fixtures/checksums and actual device/API evidence.
