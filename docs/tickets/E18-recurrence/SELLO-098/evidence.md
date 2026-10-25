# SELLO-098 — Store schedules and record due occurrences on opening

- **Revision:** the gate tested commit `2481772` on `sello-098-catch-up`, which merges `main`
  at `4ff9eaf` (parts 1 and 2, #120 and #121); the only uncommitted file was the retained
  report. The status and this file are added after that commit.
- **Requirement mapping:** recorded exactly once after a crash or a second opening →
  checkpoints moved with each batch and unique origins (`ScheduleCatchUpContractTest`
  RE12, RE13; `CatchUpOnOpeningTest`); a deleted occurrence not recorded again → RE15;
  catch-up bounded and resuming → batches of 50 across schedules, RE11 and the
  cross-schedule batch case, `CatchUpSchedulesTest`; edits for future occurrences only,
  recorded ones staying → `ScheduleCommandContractTest` RE17–RE18 and `CatchUpPending`,
  deletion keeping entries (RE24); schema migration → `ExportedSchemaOnDeviceTest` 4→5;
  stale versions and generations → the command contract; working checkpoint, opening
  after two weeks → `CatchUpOnOpeningTest` on the debug app's real graph (1, 8 and 15
  October, each once).
- **Red / Green:** part 2's `due` examples failed against a placeholder returning nothing,
  then passed. Deliberate breaks, each failing at least one test: seven in part 1
  (schema, origins, erase and restore), eight in part 2 (commands and archiving), nine in
  part 3 (checkpoint, batch limit, origins, archived category, the use case's batches and
  today, the runner's conflation, triggers and start). Two sandbox harness tests failed
  on a database lock when part 3's runner was added; the harness now ends a graph as a
  process would (`endProcess`) and they pass.
- **Tests:** domain `SchedulePolicyTest` (+3), `CatchUpSchedulesTest` (5); app host
  `CatchUpRunnerTest` (5); data device `ScheduleStorageTest` (11),
  `ScheduleCommandContractTest` (13), `ScheduleCatchUpContractTest` (11), the 4→5 upgrade,
  schedules in the erase and restore contracts; app device `CatchUpOnOpeningTest` (1).
- **Gate results:** `./scripts/verify-ticket SELLO-098 --retain` passed the local
  requirement, G1, on `2481772`: 931 host tests, zero failures, errors or skips; lint
  zero errors and 34 warnings, as on `main`. Device suites on the dedicated AVD
  `sello098_api30` (API 30): app 394 on `2481772` and data 305 on `904e6b6` (two wrapped
  lines apart), zero failures. The hosted check runs G2 on the merge result.
- **Quality run:** run 20261025T003133Z-d4df1a50; SELLO-098 G1 passed; HEAD 2481772, inputs sha256 951d1c862517, changes sha256 013272356076; report docs/tickets/E18-recurrence/SELLO-098/quality-report.json sha256 0d5169465107c069c4f8e614b278a62b1b8a3b5963d33bd60c6969de958dc099
- **Device / Artifact:** `sello098_api30` (API 30), created for this ticket; one emulator
  crash mid-run (`bad_function_call`) was rerun from a cold boot. No owner device used.
- **Review:** executor self-review; owner reviewed and merged parts 1 (#120) and 2 (#121);
  owner review of part 3 by merging.
