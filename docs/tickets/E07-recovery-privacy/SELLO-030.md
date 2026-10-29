# SELLO-030 — Implement staged atomic restore and resumable preference completion

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-028, SELLO-029, SELLO-012, SELLO-023, SELLO-004
- **Gate:** G2

### Outcome
Restore replaces financial data safely and never hides incomplete settings recovery.

### Deliverables
Read/stage/validate/preview/confirm flow with source date and replacement counts,
persisted staging/recovery IDs, atomic replacement/generation advance, surviving
restore receipt and separate resumable preferences completion. Clean private staging
on completion/cancel/expiry; route back to first/current Recibo only on truthful outcome.

### Acceptance criteria
- Invalid/canceled restore does not mutate anything. Confirmed restore applies all
  financial rows atomically and fences old drafts/commands; failure before commit rolls back.
- ADR 0003/M05: any expense/income outside 1–999,999,999,999 COP invalidates the
  entire backup before replacement. No truncation, skipped rows or partial settings
  updates; verify unchanged financial rows, generation and preferences on rejection.
- Approved D09/R01–R04: whole-file validation precedes current/replacement-count
  preview and explicit replacement warning. Never merge; restore backed-up financial
  zone and portable appearance/order, without system permission or URI-grant changes.
- Financial commit plus preference failure is “data restored, settings pending”,
  not generic failure or complete Restaurado. Restart resumes without replacing twice.
- R03 copy must explicitly confirm restored financial history and list only the
  preferences that failed to apply, with backed-up mode/color/order/direction where
  applicable. Do not show standalone ajustes pendientes or claim applied fields
  failed. Match retry action to the affected preferences and explain no second
  financial replacement. Use D09's owner-approved copy baseline with actual failed
  preference values; retain implementation-time accessible/adaptive UI review;
  test full/appearance-only/order-only failure messages and preferences-only retry.
- Unknown commit outcome recovers by original operation ID. Staging survives eligible
  restart but cannot be reinterpreted as a new operation after a generation change.
- Backup does not resurrect debug jobs, permissions or internal execution receipts.

### Tests
Real-Room rollback/reopen/generation tests, cancellation-around-commit, failing
DataStore recovery and staging cleanup; device valid/invalid restore and process
restart journeys. Extend sandbox replay/recovery scenarios; G2.

### Working checkpoint
Create backup → change data → restore → restart returns exact prior history and
preferences, with partial settings progress visibly recoverable until complete.

### Context and starting points
Restore spans two stores, so a boolean success/failure hides committed-data versus
pending-settings state. Use validated import plans from SELLO-028, document picker
from SELLO-029 and generation/receipt/prefs contracts. Read architecture §§5/7,
approved D09 and [recovery cases](../../testing/mvp-financial-examples.md#recovery-contracts-to-instantiate-in-downstream-tests--d07d09).

### Implementation plan
1. Add choose/read/private-stage/validate flow; persist owned staging ID/format/counts/
   source date and operation identity needed for eligible restart, under bounded limits.
2. Show ConfirmSlip with current/replacement counts and expected generation/revision;
   cancel leaves data untouched. If confirmation becomes stale, refresh/reconfirm safely.
3. Implement one financial replacement transaction with reference-aware ordering,
   generation advance, revision and surviving control receipt. No DataStore/file calls inside.
4. Apply portable preferences after commit and persist completion progress; on restart
   resolve operation receipt before resuming, so only pending preferences repeat.
5. Invalidate stale forms/undo/import continuation, clean owned staging and display
   committed-but-settings-pending distinctly from complete Restaurado or rollback-established error.

### Concrete cases and pitfalls
- Invalid file or canceled confirmation: zero changes in both stores.
- Restore valid G01, fail preference write: exact three expenses/one income restored,
  pending settings visible; reopening/retry must not replace financial data again.
- Cancel after Room commit: durable operation lookup recovers truth. Old generation
  submit/undo cannot cross replacement, even if restored record IDs resemble prior ones.
- Owner request, 2026-10-11 (SELLO-045, [ADR 0008](../../decisions/0008-unreadable-storage-start-over.md)):
  restore from a backup must be reachable from the screen shown when the data cannot
  be opened at start, where no financial graph exists; a restore offered only inside
  the working app is unreachable exactly then. Decide here whether the file kept
  aside as `sello-unreadable.db` can be recovered or only replaced.

### Verification recipe
Create/run RestoreCommandContractTest on real Room, `*RestoreRecoveryViewModelTest`
and RestoreJourneyTest; G2. Test failures before/inside/after commit, actual staged-file
reopen and pending preferences. Compare restored fields/order/zone/totals with original backup.

### Execution progress

- **Part 1 of 2, restore inside the app** (`sello-030-restore-in-app`). The owner chose,
  on 2026-10-22, from a concept mock-up: two parts; pending preferences kept in the
  private staged file, not a new Room table; the set-aside `sello-unreadable.db` is
  never recovered ([ADR 0008](../../decisions/0008-unreadable-storage-start-over.md));
  a restart into Recibo after a restore; the mock-up's copy and D09's approved wording.
  Delivered: Respaldo → Restaurar (open dialog, bounded read, whole-file check,
  counted preview, confirm, cancel); `RoomRestores` with one replacement transaction
  (new generation, backup zone, `history.restore` receipt), replay by operation
  identifier, staleness check, private staging that survives a restart and expires;
  preferences applied after commit and resumed at start-up; D09 pending-preferences
  slip with a retry that never replaces again; restart on leaving. Tests: 13 real-Room
  `RestoreCommandContractTest`, 12 `RestoreViewModelTest`, 3 `RestoreJourneyTest`.
- **Part 1 gate:** `./scripts/verify-ticket SELLO-030 --retain` passed on `08f37c2`
  (`main` at `025f903`), run 20261023T003800Z-8b43d9e8 on the API-30 AVD `ci30`
  (`emulator-5582`): 644 host and 648 device tests, zero failures; lint 0 errors and
  the 33 existing warnings. Mutations each turned a test red: no staleness check
  (`aChangeAfterThePreviewRefusesTheReplacementUntilPreviewedAgain`), no replay by
  receipt (`askingAgainAfterACrashBeforeTheFileMovedReplaysTheSameReplacement`). The
  system open dialog on this AVD did not return a tapped file, so screenshots come from
  `RestoreJourneyTest`, whose picker is answered by the test
  (evidence). Not the ticket's Quality run: the
  ticket stays In Progress until part 2.
- **Part 2 of 2, restore from the start-up failure screen** (`sello-030-restore-at-start`).
  Frame 6 of the mock-up: "Restaurar un respaldo" between Intentar de nuevo and Empezar
  de cero. The chosen file is read and checked whole (`BackupCodec.check`) before
  anything moves; the preview shows only the backup's date, in its own zone, and
  counts, with an explicit confirmation and no typed word (D09's word is for replacing
  data with nothing). Confirming sets the database aside, which now also clears any
  restore staged for it, opens the new store without showing it and restores into it
  through `RoomRestores`; the outcome is the shared Restaurado or D09 pending slip, and
  going on restarts in a new task and process. A failure after the set-aside is "La
  restauración no terminó", then a restart. Copy beyond frame 6 (preview, interrupted)
  is new and follows frames 2–4. Tests: `StartupTest` (+8), `StartupFailureViewModelTest`
  (+10), `StartupFailureScreenTest` (+8), real-file `RestoreAfterSetAsideTest` (2).
