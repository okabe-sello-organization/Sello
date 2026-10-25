# SELLO-116 — Fence schedule writes in the sandbox

- **Revision:** the gate tested `14c6597` on `sello-116-fence-schedules`, branched from
  `main` at `b8a8510`, which had not moved. The tree was clean; the status line, this file,
  the handoff and the retained report are added afterwards.
- **Requirement mapping:** a write after the fence closed never reaches storage →
  `ScheduleSandboxPortsTest.aScheduleCommandAfterTheFenceClosedNeverReachesStorage`,
  `catchUpIsFencedAndOnlyARecordedBatchMarksTheSandboxChanged`; only a new change or a
  recorded batch marks the sandbox changed → `aNewScheduleChangeIsReported…` and the
  catch-up test (an empty, failed or unknown batch does not); faults and log apply, and the
  diagnostic finds the receipt → `aLostScheduleAnswerCommitsOnceIsLoggedAndItsReceiptIsFound`;
  the real sandbox graph binds them → `ScheduleSandboxGraphTest`. Release builds unchanged:
  everything is in `src/debug`, and the gate's release assembly passed.
- **Red / Green:** the four `ScheduleSandboxPortsTest` cases failed against pass-through
  decorators, then passed.
- **Gate results:** `./scripts/verify-ticket SELLO-116 --retain` passed all six G1 checks on
  `14c6597`: 957 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, as on `main`. Device suites run in the hosted `quality` check. Local device
  runs on `sello115_api30`: the `devtools` (29), `feature.backup` (8), `feature.startup`
  (18) and `navigation` (61) packages, with catch-up on opening running through the fence,
  all passed.
- **Quality run:** run 20261025T052438Z-ebb0aa3c; SELLO-116 G1 passed; HEAD 14c6597, inputs sha256 f3e2f0f2c8b2, changes sha256 535d25701e7b; report docs/tickets/E18-recurrence/SELLO-116/quality-report.json sha256 e4bfc5510a2deafa537b581405a8e253b3c32de81480b1473f26e5830ecb7325
- **Device / Artifact:** synthetic AVD `sello115_api30` (API 30, 320 × 640); no screen
  changes.
- **Review:** executor self-review; owner review by merging the pull request.
