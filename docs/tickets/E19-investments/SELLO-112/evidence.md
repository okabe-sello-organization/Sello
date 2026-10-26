# SELLO-112 — Cover investments in the sandbox and catalog

- **Revision:** the gate tested commit `438a169` on `sello-112-catalog`, which merges `main`
  after SELLO-110 (#161); the only uncommitted file was the retained report. The status,
  this file, the handoff and the screenshots are added after.
- **Requirement mapping:** synthetic data, real commands → investment steps go through the
  sandbox's fenced `InvestmentCommands` and checks read the real `InvestmentReads` board
  (`InvestmentScenarioRunnerTest`, `InvestmentSandboxPortsTest`, `InvestmentSandboxGraphTest`);
  scenarios for returns, missing values, USD and corrections → `inversiones-pagina@1`,
  `valor-arrastrado@1`, `dolares@1`, `correcciones@1`, `sin-valor@1`, each reaching its
  checks fresh, resumed after a cut and restored from its backup
  (`TemporalScenarioCorpusTest`); none ships in the release build → everything is in debug
  source sets and `verifyReleaseHasNoDeveloperTools` passed with `:app:assembleRelease`;
  keep clocks distinct → scenarios move only the financial clock; catalog examples for the
  new components → *Investment answer, rows, chart and pad* (`InvestmentExamples.kt`, its
  preview, `CatalogExamplesTest`), and device tests for `SplitBar`'s gap, the values chart
  (`InvestmentPartsTest`) and the ink dock (`DockToneTest`).
- **Red / Green:** a check reading dividends for withdrawn failed `inversiones-pagina@1` at
  IN04 on the device and `InvestmentScenarioRunnerTest`, then passed; the sandbox's log and
  inspector each failed `InvestmentSandboxPortsTest` before naming investment writes; the
  ink dock test was written before its example.
- **Gate results:** `./scripts/verify-ticket SELLO-112 --retain` passed G1 full on `438a169`:
  1175 host tests in 106 suites, zero failures; lint 0 errors and 34 warnings, as on
  `main`. Device on `sello_e19b_api30` (API 30): `com.software.sello.devtools` 31/31 (also
  at 320×640 mdpi), the corpus with investment scenarios restored and rechecked;
  `InvestmentPartsTest` and `DockToneTest` 12/12. The catalog suite passed 21/22 at the
  emulator's own size; `EntryCatalogInteractionTest.expenseCategoriesEndWithANuevaButtonThatIsNotAChoice`
  fails there on a tall screen whatever this change (it scrolls where nothing scrolls) and
  passes in CI. The hosted check runs every device suite on the merge result.
- **Quality run:** run 20261026T001605Z-e646489e; SELLO-112 G1 passed; HEAD 438a169, inputs sha256 969ceb1fce91, changes sha256 91f52e97d07a; report docs/tickets/E19-investments/SELLO-112/quality-report.json sha256 e18d17b82ac366db529e0c30cf93a0bc337de52e8c152666697e9cadeb4776a3
- **Device / Artifact:** `sello_e19b_api30` and `sello_e18_api30` (API 30 emulators);
  catalog screenshots in `artifacts/`; no owner device.
- **Review:** the sandbox part was built by a delegated agent and reviewed here; executor
  self-review; owner review by merging.
