# SELLO-091 — Record contributions and withdrawals

- **Revision:** the gate tested `18361aa` on `sello-091-goal-movements`: SELLO-091's
  commits merged with `main` at `96425ac` (SELLO-090 and the runner's device selection),
  after GitHub reported the conflict with SELLO-090. The only uncommitted file was the
  status line; this file, the status and the retained report are added afterwards.
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
- **Gate results:** after resolving the conflict with SELLO-090 (routes, composition,
  navigation, strings, `GoalPolicy` and its tests, both sides kept),
  `./scripts/verify-ticket SELLO-091 --affected origin/main --retain` passed all nine G2
  checks; the affected scope selected all four device modules. Host: 877 tests; device:
  827 tests (data 269, design-system 150, app 387, catalog 21); zero failures, errors or
  skips. Lint had zero errors and 34 warnings, as on `main`. The first run, before the
  conflict, passed the full gate on `a4f34e2` with 854 host and 814 device tests.
- **Quality run:** run 20261024T211513Z-0741a6ba; SELLO-091 G2 passed; HEAD 18361aa, inputs sha256 1bfa3961fdd6, changes sha256 b8bb2bbade5e; report docs/tickets/E17-goals/SELLO-091/quality-report.json sha256 ef6712afb72a001fad2741f71c5df583f510c4e1f10224395a4f0d428b68093b; coverage affected (data,design-system,app,catalog)
- **Device / Artifact:** synthetic AVDs `sello091_api30` and `sello091b_api30` (API 30,
  320 × 640). Screenshots of
  the debug build: `artifacts/goal-chips.png`, `aporte.png`, `aporte-recorded.png`,
  `goal-dock.png`, `retiro-over-saved.png`, `correction.png`.
- **Review:** executor self-review; owner review by merging the pull request.
