# SELLO-031 — Deliver fenced two-confirmation reset and recovery

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-030, SELLO-020, SELLO-021, SELLO-004
- **Gate:** G2

### Outcome
Users can intentionally erase app data without stale drafts, undo or recovery recreating it.

### Deliverables
Counted reset preview, second confirmation requiring exact BORRAR, atomic financial
replacement/generation advance and minimal surviving completion record. Apply the
approved preference-reset policy with separate recovery; fence open forms/undo/
staging/sandbox sessions and return to truthful first-run state.

### Acceptance criteria
- Cancel/wrong confirmation leaves all data unchanged. Confirmed reset removes
  categories, budgets, expenses/income and user payloads according to the approved policy.
- Approved D09/R05–R06: counted preview then exact BORRAR confirmation; clear
  portable preferences to first-run Sistema/Cobalto/Más usadas. Next setup establishes
  device financial zone; system permissions remain unchanged. Explicitly warn that
  external backups survive; surviving control metadata contains no old amounts/names/notes.
- Old submits/undo/restore continuation cannot cross the new generation. Retained
  recovery metadata is minimal and does not preserve erased financial payloads.
- Restart/cancellation around reset resolves its original outcome; partial preference
  reset is explicit and resumes rather than fake complete or an unguarded second wipe.
- Existing external backup files are not silently deleted or claimed erased.

### Tests
Real-Room reset races, stale draft/undo/import rejection and recovery reopen;
UI counts/two confirmations; device reset→restart→new category→expense journey.
Add sandbox reset-fencing scenarios and run G2.

### Working checkpoint
Reset app can be used from empty again; canceled reset preserves the prior working
state. Backup/restore still works across the deliberate generation boundary.

### Context and starting points
Reset is deliberate data erasure; stale recovery or undo must not recreate old money.
Use SELLO-030 replacement/control patterns and actual workflows, but not the backup
payload. Read the [Sello v2 components](../../design/v2/components.html) and, until v2 designs it, the v1.1 [settings reset](../../design/sello-spec.html#s-config), architecture
§5 and approved D09's preference/zone/minimal-metadata policy.

From SELLO-019 and SELLO-021 (2026-10-12): reset must also erase `deleted_expense`
and `deleted_income`. Undo already refuses a kept copy from an earlier generation,
but the amount and note in it are erased content and must not outlive the reset.

### Implementation plan
1. Read actual affected counts at a generation/revision and show first ConfirmSlip;
   second requires exact BORRAR. Reject canceled/incorrect/stale confirmation.
2. Implement reset command with operation identity, atomic financial erase/profile
   transition/generation advance and minimum surviving completion metadata.
3. Remove erased payloads from receipts/compensation/private staging according to
   approved policy; preserve only recovery control needed to truthfully resolve this reset.
4. Apply/track preference-reset completion separately and fence open drafts/undo/import/
   debug runners. Process interruption must recover the original operation, not wipe twice.
5. Return to real empty Recibo and rerun onboarding; external backups remain outside
   app erasure scope. Explain that boundary and test reuse after reset.

### Concrete cases and pitfalls
- Type `borrar` or cancel: unchanged records/preferences. Changed counts while
  confirming must trigger the approved stale-confirmation flow, not silent erase.
- After reset, old draft/undo/import ID cannot restore an expense; recovery record
  cannot contain the entire “deleted” dataset as a hidden tombstone archive.
- Keep an external backup file intact and demonstrate deliberate new restore still
  works; reset must not pretend that external plaintext copies were erased.
- A start over after unreadable data (SELLO-045, [ADR 0008](../../decisions/0008-unreadable-storage-start-over.md))
  leaves `sello-unreadable.db` and its companion files in private storage. Reset must
  erase them too: a kept copy that survives reset is the hidden archive ruled out above.

### Verification recipe
Create/run ResetGenerationContractTest, `*ResetViewModelTest` and ResetReuseJourneyTest;
G2. Interrupt before/after commit/preferences, reopen DB, replay stale operations,
inspect retained metadata, and create a new real category/expense from empty afterward.
