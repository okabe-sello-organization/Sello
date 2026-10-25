# SELLO-111 — Set the USD rate

- **Revision:** the gate tested commit `f8807f6` on `sello-111-usd-rate`, which merges `main`
  after SELLO-108 (#158) and SELLO-109 part 1 (#159); the only uncommitted file was the
  retained report. The status, this file and the handoff are added after.
- **Requirement mapping:** rates are positive, bounded decimals with the date entered →
  `DecimalDraft` (`DecimalDraftTest`: the comma, at most two decimals, a third refused
  never rounded, six whole digits), the entry's bounds 1,00–100.000,00 and a date today or
  earlier (`UsdRateViewModelTest`: 0,99 and 100.000,01 wait, 100.000 goes, tomorrow is not
  taken, 4.180,7 is sent as 418070 hundredths with its date), and on real storage
  (`UsdRateJourneyTest`: 4.150,25 stored as 415025, a new rate replaces it and the first
  stays as an earlier one); the overview states which rate and date it used → its rate
  line "Dólar a 4.150,25 · tasa del 20 oct" (SELLO-109's `InversionesScreenTest`), opening
  Dólar once the tab is in the bar (SELLO-110); a person sets one rate with its date and
  changing it records a new dated rate → Dólar with the current rate and the history,
  newest first (`UsdRateViewModelTest`, `UsdRateScreensTest`), reached from
  Configuración's Dólar row (`SettingsViewModelTest`: current, none, unreadable); no network
  or automatic rates → nothing fetches; the pad types it.
- **Red / Green:** `DecimalDraftTest` first failed on "07" keeping its leading zero, then
  passed. Deliberate breaks, each failing its test: no recovery on a restored screen,
  Back allowed while a save's outcome is unknown, no upper bound; the comma key test
  failed with the comma left as 000.
- **Gate results:** `./scripts/verify-ticket SELLO-111 --retain` passed G1 full on
  `f8807f6`: 1114 host tests in 102 suites, zero failures; lint 0 errors and 34 warnings,
  as on `main`. Device on `sello_e19b_api30` (API 30): `UsdRateJourneyTest`,
  `UsdRateScreensTest` and `SettingsJourneyTest` 6/6 on `f8807f6`; `InvestmentPartsTest`
  7/7 with the comma pad. The hosted check runs every device suite on the merge result.
- **Quality run:** run 20261025T225304Z-81a973d9; SELLO-111 G1 passed; HEAD f8807f6, inputs sha256 c76eb67432b0, changes sha256 5150e8e7a9c0; report docs/tickets/E19-investments/SELLO-111/quality-report.json sha256 3bd45ef47f3163c068c9bd3c3809ceeedc36962851e45f05624bb4c2df631235
- **Device / Artifact:** `sello_e19b_api30` (API 30 emulator); screenshots in `artifacts/`
  (Configuración, Cambiar la tasa, Dólar); no owner device.
- **Review:** executor self-review; owner review by merging.
