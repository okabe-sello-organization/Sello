# SELLO-038 — Implement exact signed carryover domain calculations

- **Revision:** after review round 2 the gate tested commit `11878a9` on `sello-038-carryover-domain`:
  the implementation commit `54c534c`, the merge of `origin/main` at `61f70a0` (#194, #196), the round 1
  fixes and the round 2 comment-only change on top of SELLO-037's commits. `main` had not moved since the
  merge. The only uncommitted file was the retained report. The status and this file are added after.
- **Requirement mapping:** base / carry / capacity / remaining, signed, checked arithmetic →
  `SignedCarryoverPolicy.replay` and `CarryMonth`; tests `SignedCarryoverPolicyTest` SC01–SC07, SC23,
  SC24, SC40 (Long overflow of `C`, an intermediate `C` that overflows while `R` would fit, a deficit
  past the smallest `Long`, and the smallest `Long` itself fitting), SC41. Month order, transitions and
  typed failures → `replay`, `carryIn`, `enable` (CP3, SC08, SC46), `drop` (CP10, SC17–SC19),
  `CarryoverFailure` (missing history, no finite base, overlapping chains, future month); tests in the
  same class. Corrected history with a deterministic affected range → `CarryoverCorrectionReplay.preview`
  and `catchUp`; `CarryoverCorrectionReplayTest` SC25–SC33, SC37, SC47. Verdict, remaining, forecast,
  overall budget and allowance on base + carry; COP 50 allowance rounding without erasing a deficit;
  Por asignar on the base only; carry never income → `MonthlyBudgetPolicy.snapshot(carryover)`,
  `LimitedBudget.carry`/`base`, `ResumenPolicy`, `CategoryOrderPolicy`, `ReservationPolicy.reserved(chains)`;
  `CarryoverSnapshotTest` SC09–SC16 (SC14 through `ResumenPolicy`), SC35–SC39, SC41, SC06. No-carry mode
  unchanged: a snapshot with no chain is `==` to the plain one (test), and `MonthlyBudgetPolicyTest` and the
  other existing domain suites pass unchanged. Review round 1: a month of a later chain reads only that chain's history (`carryIn`, `drop`) → `SignedCarryoverPolicy.run`; tests `r1*`. Resumen's suggestion offers and ranks by the base → `ResumenPolicy.suggestion`; test `r2*` in `CarryoverSnapshotTest`. `enable` reads only the category's own defaults; test `r4*`. Boundary documentation, including the exact history range per call and every reader of `.limit` left to SELLO-041 with its correct figure → `artifacts/consumer-boundary.md`.
  Expected values are copied from [signed carryover examples](../../../testing/signed-carryover-examples.md),
  except the review round 1 tests (`r1*`, `r2*`, `r4*`), whose values are worked out by hand (`r1*` on SC19's chains)
  with `C = B + K` and `R = C − S`, each with its arithmetic in a comment;
  none is computed by production code. Not here, by the ADR 0020 mapping: SC08/SC17–SC22 commands and
  refusals as stored commands, SC34, SC42–SC45 (SELLO-039, SELLO-040).
- **Red / Green:** red first, against stubs that compiled and returned a wrong answer, so every failure was
  behavioural (for example "expected 180000 but was 100000", "expected MissingHistory but was
  Unrepresentable"): `./gradlew :domain:test --tests '*SignedCarryoverPolicyTest'` 34 tests, 32 failed;
  `--tests '*CarryoverSnapshotTest'` 20 tests, 19 failed; `--tests '*CarryoverCorrectionReplayTest'`
  16 tests, 15 failed. The tests that passed under a stub did so because the stub's answer coincided
  with the expected one. Round 1, red against the unfixed code with the new tests only: 5 of 5 failed
  (`r1AMonthOfALaterChainNeedsOnlyThatChainsHistory` and `r1DroppingInALaterChainNeedsOnlyThatChainsHistory`
  with `MissingHistory(2027-01)` where a value was expected; `r1AFullReplayStill...` with the covering chain's
  gap reported as `MissingHistory(2027-01)` instead of `2027-04`; `r2TheSuggestionOffersAndRanksByTheBaseNotTheCapacity`
  naming the wrong category and limit; `r4AnotherCategorysDefaultNever...` refused with `UnlimitedThisMonth`).
  Green after the fixes: the three classes pass (75 tests), and `./gradlew :domain:test` passes 391 tests,
  0 failures.
- **Gate results:** `./scripts/verify-ticket SELLO-038 --retain` passed G1 full (no device suites) on
  `bb67ad0`: ticket check, quality tests, performance tests, architecture, links and the Gradle host gate
  all ok; 1369 host tests in 117 suites, zero failures, errors or skips; lint 0 errors and 38 warnings.
  The Gradle results were not fresh (`fresh_results` false): Gradle reused the up-to-date results of the
  `:domain` run made just before on the same sources. The first gate run, on `1c2a499` before the round 1
  fixes, also passed (1364 tests). `:domain:ktlintCheck`, `tickets.py check` and `links.py` also pass. The run on `bb67ad0` after round 1 passed too (1369 tests); round 2 changed only comments and this file, so the change set changed and the gate was run again.
- **Quality run:** run 20261029T035901Z-c9ced46e; SELLO-038 G1 passed; HEAD 11878a9, inputs sha256 e06af4c0cabd, changes sha256 0eafeeca5291; report docs/tickets/E09-signed-carryover/SELLO-038/quality-report.json sha256 0afaf6cedaffc231e1ccf9ffb5cd6c456b028b83928c2eb96dda3d1379ab69b8
- **Device / Artifact:** not applicable: pure domain calculations, no UI, storage or device behaviour, and
  nothing a customer can switch on. The `:data` and `:app` sources compile against the new domain API.
  The hosted `quality` check on the pull request runs the ticket's gate.
- **Review:** self-review done against the acceptance criteria. Adversarial reviewer agent (Claude Opus 5.5)
  on pull request 198: round 1, CHANGES REQUIRED, findings R1 and R2 (important), R3 and R4 (minor); R1, R2
  and R4 fixed in this branch and R3 deferred to SELLO-041 by the orchestrator's ruling, with every reader
  listed in `artifacts/consumer-boundary.md` and the handoff. Round 2, a fresh reviewer: APPROVE, R1, R2 and R4 resolved, R3 ruling applied, one Minor finding, R5 (the
  record said every expected value is from the corpus, but the round 1 tests are hand-worked), fixed by saying so
  here and in the doc comments and adding the arithmetic to the tests; no code or test logic changed, and the
  orchestrator checked the fix diff. The agent reviews are never recorded as a human or
  owner approval; the merge, under the owner's delegated orchestrated run, is the approval.
