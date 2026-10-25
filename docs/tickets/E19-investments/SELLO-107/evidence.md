# SELLO-107 — Store investments, movements, values and the USD rate

- **Revision:** the gate tested commit `a6b3b35` on `sello-107-investment-storage`, which
  merges `main` at `9d11af3` (SELLO-106 done, #156); the only uncommitted file was the
  retained report. The status, this file and the handoff are added after.
- **Requirement mapping:** decimal rates, never floating point; amounts exact in their
  currency → `UsdRate` in exact hundredths and `usd_rate.rate_hundredths INTEGER` (no REAL
  column: `ExportedSchemaTest`), `InvestmentAmount` in minor units of COP or USD
  (`InvestmentPolicyTest` IN26, IN32, IN34; `InvestmentStorageTest` rate 4.150,25 → 415025);
  withdrawals above the last value are refused → `InvestmentPolicy.figures`/`available`
  (IN10, IN18) and the commands' `AboveValue`/`NoValue` (`InvestmentCommandContractTest`
  IN10, IN18, IN19, IN20); corrections recompute totals as the examples → IN19–IN22 on
  real Room and the board's figures (IN04 −258.000, IN27 +USD 50,50, IN28 overview);
  domain models, schema migration, commands with receipts and undo, reads with typed
  failures → `Investments.kt`, `InvestmentCommands.kt`, `Migration5To6` with
  `ExportedSchemaOnDeviceTest` (a real schema-5 database keeps every row),
  `RoomInvestmentCommands` (replay, conflict, stale generation, versions, Deshacer within
  six seconds), `RoomInvestmentReads` and `InvestmentBoardObservationTest` (a failed read
  keeps the last good board, never none); USD returns stay in USD and only the total
  converts → rule 10 and 13, IN27–IN30.
- **Red / Green:** policy: 28 example tests passed, and five deliberate breaks each failed
  them (an aporte not carried forward: 6 failures; no withdrawal limit: 2; the return
  rounded down: 4; the rate by entry order only: 1; the sign from the rounded return: 1).
  Storage, by the delegated agent: four breaks (no history check on record; none on
  delete or the last aporte; undo not putting the row back; the last entered rate as
  current) failed IN10, IN19, IN22, IN21 and IN31, then passed. The named rejections
  `InvalidAmount` and `InvalidDate` replaced `Unrepresentable` with a test that fails on
  the earlier code.
- **Gate results:** `./scripts/verify-ticket SELLO-107 --retain` passed G1 full on `a6b3b35`:
  1076 host tests in 98 suites, zero failures; lint 0 errors and 34 warnings, as on
  `main`. Device suites, `sello_e18_api30` (API 30): `:data` 358/358 on `427556a`;
  `InvestmentCommandContractTest` 28/28 on `4e76527` (`sello_e19b_api30`); app
  `FinancialStorageOnDeviceTest` and `TestDataTest` 8/8. The hosted check runs them on the
  merge result.
- **Quality run:** run 20261025T214402Z-fa7a1ad3; SELLO-107 G1 passed; HEAD a6b3b35, inputs sha256 61cf0b1afd8f, changes sha256 726e03fb6eab; report docs/tickets/E19-investments/SELLO-107/quality-report.json sha256 e5223753098b9b735c865a948aa75ebc847c571c97553d2e4d8b5215c926ec4b
- **Device / Artifact:** emulators `sello_e18_api30` and `sello_e19b_api30` (API 30); no
  owner device.
- **Review:** executor self-review of the contract, the policy and the delegated data
  layer; owner review by merging.
