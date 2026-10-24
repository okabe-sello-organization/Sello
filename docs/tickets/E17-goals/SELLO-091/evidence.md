# SELLO-091 — Record contributions and withdrawals

- **Revision:** the gate tested `a4f34e2` on `sello-091-goal-movements`, on `main` at
  `52a377b`; the only uncommitted files were this ticket's bookkeeping (Execution progress,
  handoff, artifacts). This file, the status and the retained report are added afterwards.
- **Requirement mapping:** a withdrawal above saved is refused with a clear message →
  `GoalPolicy.withMovement`, `GoalMovementViewModel`, `GoalMovementScreen`;
  `GoalPolicyTest.mt08…`, `GoalMovementViewModelTest.mt08…`,
  `GoalMovementScreenTest.mt08…` ("Más de lo guardado (1.710.000)", the button waits),
  `GoalMovementJourneyTest.mt08…`; corrections a later retiro needs → `mt15…` in all three
  layers. Past dates allowed, future refused → `GoalPolicy.acceptsDate` in the form and the
  date picker; `mt20AndMt21…`. Unknown outcomes are recovered before another write →
  `anUnknownOutcomeIsAskedAbout…`. Movement history with edit, delete and undo →
  `editingAnAporte…`, `deletingAMovement…`, `ExpenseCorrectionsTest` goal cases,
  `GoalMovementJourneyTest.aMovementIsCorrectedThenDeletedAndPutBackWithDeshacer`.
  Contributions never touch budgets → the journey asserts no expense, income or limit row.
  Restoration → `afterProcessDeathTheDraftAndTheOpenNoteAreBack`, a recreated form in the
  journey. Duplicate submit → `fromAGoalAnAporte…` presses twice, one command.
- **Red / Green:** `GoalPolicyTest`: the three movement checks failed against a stub,
  then 55/55 passed. `ExpenseCorrectionsTest`: four goal cases failed (no offer, no restore
  through goal commands), then 29/29 passed. `GoalMovementViewModelTest`: 12/12 failed
  against a stub, then passed. The chip test failed (no percentage), then passed. Screen
  and journey tests were written after the screen; a deliberate break (the button ignoring
  what is saved) failed two screen tests.
- **Gate results:** `./scripts/verify-ticket SELLO-091 --retain` passed all nine G2
  checks, full coverage. Host: 854 tests; device: 814 tests (data 269, design-system 144,
  app 380, catalog 21); zero failures, errors or skips. Lint had zero errors and 34
  warnings, as on `main`.
- **Quality run:** run 20261024T200938Z-043cee08; SELLO-091 G2 passed; HEAD a4f34e2, inputs sha256 cbdc46b6d4ed, changes sha256 6e78c243356a; report docs/tickets/E17-goals/SELLO-091/quality-report.json sha256 6ea2aa0432f36eda1bc34efe7fb58dcdaa4f8e8a616e1fe73929e6d1469691e8
- **Device / Artifact:** synthetic AVD `sello091_api30` (API 30, 320 × 640). Screenshots of
  the debug build: `artifacts/goal-chips.png`, `aporte.png`, `aporte-recorded.png`,
  `goal-dock.png`, `retiro-over-saved.png`, `correction.png`.
- **Review:** executor self-review; owner review by merging the pull request.
