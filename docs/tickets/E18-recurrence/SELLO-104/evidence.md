# SELLO-104 — Cover recurrence in the sandbox

- **Revision:** the gate tested commit `51abcc5` on `sello-104-recurrence-sandbox-part-2`,
  which merges `main` at `32e1d71` (SELLO-103 done, #135); the only uncommitted file was
  the retained report. The status, this file, the handoff and the epic's status are added
  after. Part 1 merged in #132.
- **Requirement mapping:** scenarios with financial-clock jumps and checks of recorded
  occurrences → part 1's nine *Programados* scenarios (RE01, RE05–RE07, RE09–RE11,
  RE15–RE24); checks of reservations → `pagos-previstos@1` with the figures `separado`,
  `margen` and `por día` on the real snapshot (RE25–RE29, RE26 after catch-up records
  the rent); the real catch-up path → **Ponerse al día** runs the app's `CatchUpRunner`
  over the fenced ports; none in the release build → the scenarios live in the debug
  source set and every gate checks the release APK; `Scenario.kt`, the sandbox guide and
  the corpus no longer say recurrence does not exist; working checkpoint, each scenario
  reaches its check → `TemporalScenarioCorpusTest` plays and restores all of them. The
  notice is checked by SELLO-103's `CatchUpNoticeJourneyTest` on `semana-fuera@1`.
- **Red / Green:** part 2: the `margen` figure read as the plain remaining amount fails
  `pagos-previstos@1` in both corpus tests ("esperado 400.000, real 600.000", RE25).
  Part 1's breaks are listed in #132.
- **Tests:** host `ScenarioDefinitionTest`, `ScenarioRunnerTest` and
  `RecurrenceScenarioRunnerTest` (61 devtools tests); device `TemporalScenarioCorpusTest`
  2 and `ScenarioReplayTest` 6, which play every scenario on real Room, and
  `CatchUpNoticeJourneyTest` 1.
- **Gate results:** `./scripts/verify-ticket SELLO-104 --retain` passed G1 on `51abcc5`:
  1043 host tests, zero failures; lint 0 errors and 34 warnings, as on `main`. Device
  tests on `sello098_api30` (API 30) on `51abcc5`: `TemporalScenarioCorpusTest` 2/2,
  `ScenarioReplayTest` 6/6, `CatchUpNoticeJourneyTest` 1/1. The hosted check runs G2 on
  the merge result.
- **Quality run:** run 20261025T160515Z-33941dd6; SELLO-104 G1 passed; HEAD 51abcc5, inputs sha256 ecb836478221, changes sha256 baae189e9135; report docs/tickets/E18-recurrence/SELLO-104/quality-report.json sha256 a3d0291a77bda173499c7345f2eae83f611805a7e7c59cb3d1c451db79e7f304
- **Device / Artifact:** `sello098_api30` (API 30); no owner device; synthetic data.
- **Review:** executor self-review; part 1 built by a delegated agent and reviewed here;
  owner review by merging.
