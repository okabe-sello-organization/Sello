# SELLO-102 — Show expected income in Plan del mes

- **Revision:** the gate tested commit `0bf4d37` on `sello-102-plan-del-mes`, cut from
  `main` at `b8a8510` (SELLO-099 done, #126), which is still `main`; the only uncommitted
  file was the retained report. The status, this file, the handoff and the screenshots
  are added after that commit.
- **Requirement mapping:** expected money never enters recorded totals → the plan is its
  own state, apart from the hero and the list (`IncomeHistoryViewModelTest` BG-E08: the
  split still says 800.000 short; `IncomeHistoryScreenTest` BG-E08: the list's total is
  what was recorded); an expected income becomes recorded on its date → once catch-up
  records it the plan drops it, and the two reads are joined only at one revision
  (`IncomePlanPolicyTest` RE36, `IncomeHistoryViewModelTest` BG-E09); no plan for a past
  month or without expected income (`aPastMonthHasNoPlan`, `withNothingExpectedThereIsNoPlan`,
  `IncomePlanPolicyTest`); the catalog shows each new part in all themes and at large
  text (*Plan slip and expected chip*, previews in every theme at double font,
  `PlanPartsTest` in the four schemes and at double font); working checkpoint, Plan del
  mes showing the expected salary until its date → BG-E08 and BG-E09 above.
- **Red / Green:** the policy tests failed on the stub (4 of 5), then passed. Deliberate
  breaks each fail a test: no revision check (2 tests), no month check, a failed
  schedule read shown as no plan, an unsigned negative plan, the tag without its spoken
  name, a tappable chip, the plan not drawn (4 screen tests), a failed plan not drawn.
  The month and chip breaks first stayed green; those tests were strengthened until
  they failed.
- **Tests:** domain `IncomePlanPolicyTest` (5); `IncomeHistoryViewModelTest` (+7);
  `CatalogExamplesTest` (new id); device `PlanPartsTest` (3), `IncomeHistoryScreenTest`
  (+5, and its "no plan" check now looks for the plan's tag, since the drawn tag is in
  capitals).
- **Gate results:** `./scripts/verify-ticket SELLO-102 --retain` passed the local
  requirement, G1, on `0bf4d37`: 965 host tests, zero failures, errors or skips; lint
  zero errors and 34 warnings, as on `main`. An earlier run on `a3508ff` failed ktlint on
  one long line, fixed in `0bf4d37`. Device suites on the dedicated AVD
  `sello098_api30` (API 30) on `0bf4d37`: design-system 160, catalog 21, app 412, zero
  failures. The hosted check runs G2 on the merge result.
- **Quality run:** run 20261025T051839Z-e75d36f6; SELLO-102 G1 passed; HEAD 0bf4d37, inputs sha256 cd60eb19aa07, changes sha256 1aff6420a674; report docs/tickets/E18-recurrence/SELLO-102/quality-report.json sha256 4e34a1128060e512b1b58e7f5a56a52b616a43712210c062f4f41cb54fedcd5e
- **Device / Artifact:** `sello098_api30` (API 30), created for this epic; no owner
  device used. Catalog screenshots in `artifacts/` (synthetic figures).
- **Review:** executor self-review; owner approved the plan and the footnote wording;
  owner review by merging.
