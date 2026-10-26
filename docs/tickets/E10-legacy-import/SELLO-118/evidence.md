# SELLO-118 — Import FinanceTracker investments once Inversiones exists

- **Revision:** the gate tested commit `bcc8fd7` on `sello-118-legacy-investments`, `main`
  at `7922e73` (with SELLO-044) merged in; no other file was dirty. The retained report, this file and the Done status are added after that commit.
- **Requirement mapping:** ADR 0019 rules hold, dependants go with their investment, only
  the owner's repairs, the released validator accepts the result unchanged →
  `LegacyTranslator.investments`/`history`/`usdRates`, proved by `LegacyTranslatorTest`
  (6 new cases: old order and currencies, interest as value, added first value, every
  investment skip reason, rates, merchant rules; each round-trips through `BackupCodec`);
  exact minor units and currency, no floating-point rate → the rate is read from the JSON
  number's text as `BigDecimal` and rounded half up only beyond two decimals (owner,
  2026-10-25); every investment, movement and rate imported or counted → report lines in
  the same tests; restored on real Room → `LegacyImportRestoreTest` (investment, movements,
  next position and rate rows written out by hand).
- **Red / Green:** `./gradlew :data:testDebugUnitTest --tests "*LegacyTranslatorTest*"`: the 6 new and updated translator tests failed against the SELLO-044
  translator (everything reported as not imported yet); implemented, 29 of 29 passed.
- **Owner's real export (read locally only, nothing committed):** a deleted probe test wrote
  Sello's figures to `/tmp`, and a script compared them with FinanceTracker's ledger
  replay, printing only match/no match: all 11 investments match on value, aportes and
  dividends; 109 movements (107 plus 2 added first values); the USD rate rounded as
  expected; EUR and GBP rates left out; the validator accepted the translation.
- **Gate results:** `./scripts/verify-ticket SELLO-118 --retain`: G1 full passed
  (tickets-check, quality-tests, performance-tests, architecture, links, gradle-host).
- **Quality run:** run 20261026T025200Z-4a61d11a; SELLO-118 G1 passed; HEAD bcc8fd7, inputs sha256 b455299314b7, changes sha256 f937e59f3204; report docs/tickets/E10-legacy-import/SELLO-118/quality-report.json sha256 1ed5dacc0b21110e4e6098453d883b5eab5e7aa07645a9b5efd42afad25d851e
  (rerun after #154 merged and `main` was merged in; the tree is unchanged from `8cbd522`'s gate run, `20261026T022250Z-834ead15`)
- **Device / Artifact:** focused runs on a new API 30 emulator (`sello118_api30`, `emulator-5732`,
  deleted afterwards): `:data` `LegacyImportRestoreTest` 3 of 3 passed; `:app`
  `RestoreJourneyTest` 5 of 5 passed. The full device suites run in the hosted `quality`
  check on the merge result.
- **Review:** self-review against ADR 0019 and the investment rules; the merge of this
  ticket's pull request is the owner's approval.
