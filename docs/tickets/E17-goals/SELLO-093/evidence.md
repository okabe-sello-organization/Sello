# SELLO-093 — Show one goal on Recibo

- **Revision:** the gate tested `dfdf66d` on `sello-093-recibo-goal`, branched from `main`
  at `f7e9153` (SELLO-092 and SELLO-098 part 1); `main` had not moved since. The tree was
  clean; this file, the status, the progress notes, the handoff and the retained report are
  added afterwards.
- **Requirement mapping:** the person's choice wins, otherwise the highest completion,
  nothing with no active goal → `GoalPolicy.featured` (`GoalPolicyTest.mt28…`–`mt31…`) and
  real Room (`GoalCommandContractTest.mt28To30…`), both from SELLO-087;
  `ReciboGoalViewModelTest.mt28AndMt29…`, `nothingIsShownWithNoActiveGoal…`;
  `ReciboGoalJourneyTest.mt28ToMt30…` (the choice made on the goal page, then completed).
  The fallback when the shown goal is completed or deleted → `mt30…` in both. Not shown on a
  past month → `nothingIsShownOnAPastMonth…`, `onAPastMonthNoGoalIsShown`. The green 100 %
  row → `anotherGoalAt100Percent…` (view model, screen) and
  `anotherGoalAt100PercentIsMarkedLogradaFromItsGreenRow` (journey); pressed twice, one
  completion → `theWaitingGoalIsMarkedLogradaFromItsRowOnce…`. The exact percentage stays
  accessible → `ReciboGoalScreenTest.thePillSaysItsExactPercentage…` (description and
  progress 0.57). Large fonts → `atTwiceTheFontSize…`. A failed read → `aFailedReadIs…`
  (view model, screen). Recibo's answer never changes with a goal → the journeys compare the
  summary word for word before and after.
- **Red / Green:** `ReciboGoalViewModelTest`: 6 of 7 failed against a stub that always hid
  the goal; the seventh (nothing shown) passed against it by construction; then 7/7. Screen
  and journey tests were written after the screen. The journey first failed on its own
  navigation (the goal page has no tab bar) and once on timing (it checked before the pill
  had switched); with a wait it passed three runs in a row.
- **Gate results:** `./scripts/verify-ticket SELLO-093 --retain` passed all six G1 checks on
  `dfdf66d`: 925 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, none in files changed here. Device suites run in the hosted `quality` check.
  Local focused device runs on `sello092_api30`: `feature.goals` (37 tests),
  `feature.recibo` (70), `navigation` (61), and the design-system and catalog suites, all
  passed; the Recibo goal tests and both suites passed again after the last layout change.
- **Quality run:** run 20261024T235153Z-623b0f7f; SELLO-093 G1 passed; HEAD dfdf66d, inputs sha256 2dcd5cd67a8c, changes sha256 bdbcd7d0c3c1; report docs/tickets/E17-goals/SELLO-093/quality-report.json sha256 b0a4891e26db3c905ea514b293655197fc32914a1e62b7add5f4d733fdbecf3c
- **Device / Artifact:** synthetic AVD `sello092_api30` (API 30, 320 × 640; one capture at
  400 × 800). Screenshots of the debug build: `artifacts/recibo-goal.png`,
  `recibo-goal-wide.png`, `marcar-from-recibo.png`, `after-marcar.png`,
  `aportar-ticket.png`, `past-month.png`.
- **Review:** executor self-review; owner review by merging the pull request.
