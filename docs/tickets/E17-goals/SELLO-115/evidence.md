# SELLO-115 — Finish what E17 left open

- **Revision:** the gate tested `dfe788a` on `sello-115-goal-follow-ups`: SELLO-115's commits
  merged with `main` at `b27bad5` (SELLO-117, #127), with the epic set to In Progress for the
  run. The tree was clean; the status lines, this file, the handoff
  and the retained report are added afterwards.
- **Requirement mapping:** two stamps land in turn, reduced motion at once →
  `StampLandingStaggerTest` (at 100 ms the first is coming down and the second is not
  drawn; after 2 s both are; reduced motion both at once). `metas-tarde` refuses the
  Deshacer as `UndoExpired` and checks the goal completed → `TemporalScenarioCorpusTest`
  (fresh = resumed, cut after step 2; restored from a backup, 1 final figure matches),
  `GoalScenarioRunnerTest` (the step's window reaches `CompleteGoal`; 34 s by default),
  `ScenarioDefinitionTest`. Recibo's first use shows the goal →
  `ReciboScreenTest.theFirstRunStillShowsTheGoalUnderItsCard`,
  `ReciboGoalJourneyTest.withNoCategoriesYetTheFirstUseScreenStillShowsTheGoal`; with no
  active goal nothing is added (the goal area stays hidden, `ReciboGoalViewModelTest`).
  The catalog lists `goals.band` with previews → `CatalogExamplesTest`; the scaffold's
  Metas dock → `CatalogShellTest.theScaffoldExampleShowsMetasGreenDockOnRequest`.
- **Red / Green:** `StampLandingStaggerTest` failed with the second stamp already drawn
  (747 pixels) while the first landed, then passed. The first-use screen test failed with
  the goal not displayed, then passed. `GoalScenarioRunnerTest` failed with 34 s sent
  instead of the step's zero window, then passed. `CatalogExamplesTest` failed on the
  missing `goals.band`, then passed.
- **Gate results:** `./scripts/verify-ticket SELLO-115 --retain` passed all six G1 checks on
  `dfe788a`: 953 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, as on `main`. Device suites run in the hosted `quality` check. Local device
  runs on `sello115_api30`: the full design-system (159) and catalog suites, the
  `devtools` package (every scenario, `metas-tarde` included), `feature.goals` (38),
  `feature.recibo` (71) and `UiAcceptanceSweepTest`, all passed.
- **Quality run:** run 20261025T054500Z-24f44634; SELLO-115 G1 passed; HEAD dfe788a, inputs sha256 b371712f1172, changes sha256 64a0bd67ab6c; report docs/tickets/E17-goals/SELLO-115/quality-report.json sha256 ecabaaae9ff3c569ae795c3776f2ac62df4d38d79eb7b37db4ee9bd112c06ec8
- **Device / Artifact:** synthetic AVD `sello115_api30` (API 30, 320 × 640):
  `artifacts/recibo-first-use-goal.png`, `catalog-goals-band.png`, `catalog-metas-dock.png`.
- **Review:** executor self-review; owner review by merging the pull request, which also
  proposes E17 Done again.
