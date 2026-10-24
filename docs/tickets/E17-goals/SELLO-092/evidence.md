# SELLO-092 — Earn stamps and complete goals

- **Revision:** the gate tested `ec739a7` on `sello-092-goal-celebrations`: SELLO-092's
  commits merged with `main` at `1e4ac2d` (SELLO-097 and the host-gate rule), plus the
  style fixes the first run found. The tree was clean; this file, the status, the
  progress notes, the handoff and the retained report are added afterwards.
- **Requirement mapping:** stamps follow the approved examples, faded stamps after a
  withdrawal or target change included → `GoalPolicy` (MT01–MT19, from SELLO-087),
  `MilestonePlace` relight (`MilestoneRelightTest`), `GoalMovementViewModelTest`
  celebration cases, `GoalEditorViewModelTest.mt18…`/`mt17…`,
  `GoalCelebrationJourneyTest.mt02…`, `fromMetasTheStampLandsOnAPaperTicket`,
  `mt18ANewTarget…`. Celebrations follow only a confirmed save → the movement, editor and
  completer tests hold the command and see nothing until the receipt; replays and
  recovered saves celebrate nothing (`aReplayOrAnAporte…`, `mt17…`,
  `anUnknownOutcomeIsAskedAbout…`). A reopened screen is not a new crossing →
  `ExpenseCorrectionsTest.aStampLandsOnce…`. Marcar como lograda with Deshacer →
  `GoalCompleterTest` (MT23, 14 s / 34 s windows including the 4 s moment, pressed twice
  sent once, process death), `ExpenseCorrectionsTest` MT24/MT25, `GoalsViewModelTest.mt23…`,
  `GoalEditorViewModelTest.mt26…`, journeys `mt23AndMt24…`, `mt26…`. Final and read-only
  after the window → `afterTheDeshacerWindowTheGoalStaysLogradaAndReadOnly`. Reduced motion
  → `MilestoneRelightTest.withReducedMotionItIsLitAtOnce`; landing and the overlay keep
  their existing reduced-motion paths.
- **Red / Green:** `GoalMovementViewModelTest` celebration cases, `GoalCompleterTest` and
  the completion cases of `ExpenseCorrectionsTest` and `GoalsViewModelTest` failed against
  stubs, then passed. `GoalCompleterTest.pressedTwiceItIsSentOnce` exposed a real defect (a
  second completion during the moment), fixed by completing only from Idle or Failed.
  `GoalEditorViewModelTest.mt18…` failed with no notice, then passed.
  `aStampLandsOnce…` failed with every request landing, then passed. Device journeys were
  written after the screens; two failed on test selectors (a stamp shown twice, the plate in capitals) and were corrected.
- **Gate results:** `./scripts/verify-ticket SELLO-092 --retain` passed all six G1 checks
  on `ec739a7`: 917 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, none in files changed here. The first run on `a3d56c9` failed ktlint (a
  detached KDoc) and added a lint warning (modifier order); both fixed. Device suites run
  in the hosted `quality` check. Local focused device runs on `sello092_api30`: the
  `feature.goals` package (27 tests), `ConfirmationNoticeShellTest`,
  `UiAcceptanceSweepTest`, and the design-system and catalog suites, all passed.
- **Quality run:** run 20261024T225742Z-6cccd3cb; SELLO-092 G1 passed; HEAD ec739a7, inputs sha256 acdf6adde815, changes sha256 fd00cc110d03; report docs/tickets/E17-goals/SELLO-092/quality-report.json sha256 3c0b4d7e1e9f4669e62cded75a9b68094f18872ebe56ae2addfa6afb6d911abd
- **Device / Artifact:** synthetic AVD `sello092_api30` (API 30, 320 × 640). Screenshots of
  the debug build: `artifacts/stamp-on-goal.png`, `stamp-ticket-metas.png`, `marcar.png`,
  `lograda-moment.png`, `lograda-deshacer.png`, `editor-done-ask.png`.
- **Review:** executor self-review; owner review by merging the pull request.
