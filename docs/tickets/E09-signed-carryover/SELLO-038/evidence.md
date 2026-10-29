# SELLO-038 — Implement exact signed carryover domain calculations

- **Revision:** the gate tested commit `1c2a499` on `sello-038-carryover-domain`: the implementation
  commit `54c534c` on top of SELLO-037's commits, with `origin/main` at `61f70a0` (#194, #196) merged
  once. The merge had no conflicts. The only uncommitted file was the retained report. The status, this
  file and the handoff are added after.
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
  other existing domain suites pass unchanged. Boundary documentation → `artifacts/consumer-boundary.md`.
  Expected values are copied from [signed carryover examples](../../../testing/signed-carryover-examples.md);
  none is computed by production code. Not here, by the ADR 0020 mapping: SC08/SC17–SC22 commands and
  refusals as stored commands, SC34, SC42–SC45 (SELLO-039, SELLO-040).
- **Red / Green:** red first, against stubs that compiled and returned a wrong answer, so every failure was
  behavioural (for example "expected 180000 but was 100000", "expected MissingHistory but was
  Unrepresentable"): `./gradlew :domain:test --tests '*SignedCarryoverPolicyTest'` 34 tests, 32 failed;
  `--tests '*CarryoverSnapshotTest'` 20 tests, 19 failed; `--tests '*CarryoverCorrectionReplayTest'`
  16 tests, 15 failed. The tests that passed under a stub did so because the stub's answer coincided
  with the expected one. Green after the implementation: the three classes pass (70 tests), and
  `./gradlew :domain:test` passes 386 tests, 0 failures, before and after merging `main`.
- **Gate results:** `./scripts/verify-ticket SELLO-038 --retain` passed G1 full (no device suites) on
  `1c2a499`: ticket check, quality tests, performance tests, architecture, links and the Gradle host gate
  all ok; 1364 host tests in 117 suites, zero failures, errors or skips; lint 0 errors and 37 warnings.
  The Gradle results were not fresh (`fresh_results` false): Gradle reused the up-to-date results of the
  `:domain` run made just before on the same sources. `:domain:ktlintCheck`, `tickets.py check` and
  `links.py` also pass.
- **Quality run:** run 20261029T034441Z-2a8acf78; SELLO-038 G1 passed; HEAD 1c2a499, inputs sha256 d2e233241ae8, changes sha256 2c4740475ef9; report docs/tickets/E09-signed-carryover/SELLO-038/quality-report.json sha256 28648ef6c124836083020b7c93b24ae8306526d957905380f23f1d75555ab3f4
- **Device / Artifact:** not applicable: pure domain calculations, no UI, storage or device behaviour, and
  nothing a customer can switch on. The `:data` and `:app` sources compile against the new domain API.
  The hosted `quality` check on the pull request runs the ticket's gate.
- **Review:** self-review done against the acceptance criteria. Adversarial reviewer agent (Claude Opus 5.5)
  review on the pull request; the merge, under the owner's delegated orchestrated run, is the approval.
