# SELLO-090 — Create, edit and delete goals

- **Revision:** the gate tested `16ddb03` on `sello-090-goal-editor` (SELLO-090's commit
  `0339f6d` merged with `main` at `52a377b`); the only uncommitted files were this ticket's
  bookkeeping (status, Execution progress, handoff, artifacts). This file and the retained
  report are added to that commit afterwards.
- **Requirement mapping:** target amount on the Sello pad, date optional →
  `GoalEditorViewModel` (`AmountDraft`, `SlidingKeypad`, Para cuándo); `GoalEditorViewModelTest`
  `mt34…`, `mt36…`, `mt38…`, `thisMonthOrAnEarlierOneIsNotOffered`, `GoalFormPartsTest`,
  `GoalEditorJourneyTest.aGoalTypedOnThePadWithAMonthAppearsInMetasWithItsPlan`. Projected
  months state their assumed pace in whole pesos → `GoalPolicy.pace`, `GoalPolicy.withTarget`;
  `mt34…` (769.231 for 26 months; may 27, nov 27, jun 28, dic 28), `mt35…` (692.308),
  `mt17EditingTheTargetPreviews…`, `GoalPolicyTest.mt17/mt18/mt35…`, the "Si aportas …
  cada mes" caption in `GoalEditorScreenTest`. A completed goal can only be deleted →
  `aCompletedGoalCanOnlyBeDeleted`, `aCompletedGoalOffersOnlyDeletion`. Deletion with the
  confirm slip → `mt27…`, `aGoalIsEditedThenDeletedOnlyAfterTheConfirmSlip`. Process-death
  restoration → `afterProcessDeathTheDraftIsBack…`, `aDraftWithoutASaveIsRestored…`,
  `aDraftSurvivesRecreation…`.
- **Red / Green:** `GoalPolicyTest`: the two preview tests failed against a stub returning
  the unchanged progress, then 56/56 passed. `GoalFormPartsTest`: three failed (no green
  circle, no slide, keys not reported), then 6/6 passed. `GoalEditorViewModelTest`: 17 of
  18 failed against a stub, then passed; doing so exposed a real defect, a second Crear
  meta after a confirmed save creating the goal twice, fixed by ending `canSubmit` once
  the form has a result. `DateLabelsTest`: the projected-month label failed, then passed.
  The screen and journey tests were written after the screen; a deliberate break (the
  action always enabled) failed two screen tests. `MetasJourneyTest` now expects the
  empty tab's **Crear una meta** dock.
- **Gate results:** `./scripts/verify-ticket SELLO-090 --retain` passed all nine G2
  checks, full coverage. Host: 857 tests; device: 819 tests (data 269, design-system 149,
  app 380, catalog 21); zero failures, errors or skips. Lint had zero errors and 34
  warnings, as on `main`.
- **Quality run:** run 20261024T192823Z-f0d89dde; SELLO-090 G2 passed; HEAD 16ddb03, inputs sha256 43ebfdb6ce3a, changes sha256 24dfdef0f802; report docs/tickets/E17-goals/SELLO-090/quality-report.json sha256 243a2f5af5d0b4c2063154e0f2262df57986635507832d46c3af99128fd6edd4
- **Device / Artifact:** synthetic AVD `sello090b_api30` (API 30, 320 × 640). Screenshots of
  the debug build creating, planning, editing and deleting a goal: `artifacts/new-goal.png`,
  `pad.png`, `plan.png`, `created-in-metas.png`, `edit.png`, `delete-confirm.png`.
- **Review:** executor self-review; owner review by merging the pull request.
