# SELLO-104 — Cover recurrence in the sandbox

- **Revision:** the gate tested commit `d254132` on `sello-104-close-e18`, which merges
  `main` at `32e1d71` (SELLO-103 done, #135) into part 2 (`a15f2fd`, `10437a2`, `434d3f5`);
  the only uncommitted file was the retained report. Part 1 merged as #132. The status,
  the epic's status and this file are added after.
- **Requirement mapping:** scenarios use the real catch-up path → **Ponerse al día** runs
  the app's own `CatchUpRunner` through the fenced commands (part 1,
  `RecurrenceScenarioRunnerTest`), and every scenario reaches its checks on real Room,
  fresh and resumed after a cut (`TemporalScenarioCorpusTest`); financial-clock jumps with
  checks of recorded occurrences → the nine part-1 scenarios (RE01, RE05–RE11, RE15–RE24);
  checks of reservations → `pagos-previstos@1` reads `separado` (`Q`), `margen` (`M`) and
  `por día` from the month's snapshot for RE25–RE29 and RE26, values worked by hand
  from the recurrence examples, not by the policy; the notice → SELLO-103's
  `CatchUpNoticeJourneyTest` on `semana-fuera@1` (RE10's four); `Scenario.kt`, the
  sandbox guide and the corpus no longer say recurrence does not exist → part 1, and the
  corpus now lists `pagos-previstos@1`; none ships in the release build → scenarios live
  in `app/src/debug`, and the gate's architecture and release checks passed; clocks kept
  distinct → the scenario only moves the financial clock (`AdvanceTo`).
- **Red / Green:** with `separado` read as 0 in `ScenarioRunner`,
  `TemporalScenarioCorpusTest#everyScenarioMeetsItsFiguresAndResumesToTheSameHistory`
  failed on `sello_e18_api30` with "pagos-previstos@1: Comprobación: no coincide"; reverted,
  the class passed 2/2, logging "pagos-previstos@1: 15 steps, 5 checks, 16 figures
  matched, fresh = resumed (cut after step 7)" and "backup 3607 bytes restored; same
  rows; 4 final figures match". Host `:app:testDebugUnitTest --tests '*devtools*'` passed.
- **Gate results:** `./scripts/verify-ticket SELLO-104 --retain` passed G1 full on
  `d254132`: 1043 host tests in 97 suites, zero failures; lint 0 errors and 34 warnings,
  as on `main`. The hosted `quality` check runs the device suites on the merge result.
- **Quality run:** run 20261025T160514Z-9ce8d6ad; SELLO-104 G1 passed; HEAD d254132, inputs sha256 ecb836478221, changes sha256 baae189e9135; report docs/tickets/E18-recurrence/SELLO-104/quality-report.json sha256 568c473477b30c83b30db4bba392414042ff447b8a5e1c105652a1d89c3ddd66
- **Device / Artifact:** `sello_e18_api30` (API 30, emulator), the corpus class above on
  `10437a2`; no owner device.
- **Review:** executor self-review of part 2, written by an earlier session and checked
  here; owner review by merging. This pull request also closes E18.
