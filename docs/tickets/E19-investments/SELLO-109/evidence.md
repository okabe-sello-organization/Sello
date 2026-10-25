# SELLO-109 — Add the Inversiones tab overview

- **Revision:** the gate tested commit `1f000a6` on `sello-110-investment-detail`, which
  merges `main` after part 1 (#159) and SELLO-111 (#160); the only uncommitted file was the
  retained report. This ticket closes in SELLO-110's pull request, where the tab joins the
  bar; part 1 merged as #159. The status, this file and the handoff are added after.
- **Requirement mapping:** Inversiones is a main tab leading with value, a Gana / Pierde
  stamp and the signed simple return → the fourth `ShellTab` (`NavigationStateTest`), the
  answer slip with the value in ink and the return (`InversionesScreenTest`,
  `InvestmentJourneyTest` IN03 on the real app: "Tus inversiones valen, 10.746.000 pesos,
  Gana", +7,5 %); the total converts USD at the current rate and says its date →
  `InvestmentsRenderModelTest` IN28 and the rate line "Dólar a 4.150,25 · tasa del 20 oct"
  (`InversionesScreenTest`); an investment without a value is left out of the total and
  only its row says so → IN17 and IN35 (`InvestmentsRenderModelTest`, screen test);
  loading, empty and failed reads are distinct, never zero → render-model and screen
  tests; capital and gain, distribution, each value's date → breakdown, the bar with a
  2dp gap and rows "… · valor del 12 jun"; goals are never added to the total → the board
  holds investments only (IN36, `InvestmentPolicyTest`); screen tests across windows and
  themes → every figure whole at 1.0, 1.3 and 2.0 on 320, 600 and 840dp in four themes.
- **Red / Green:** reverting each design-system extension failed its own device test (six
  of six: Pierde's ink, the comma's width, the even return, the repeating shades, the
  bar's gap, the whole caption); the three-tab guard (`NavigationStateTest`) failed until
  the tab was deliberately added with SELLO-110.
- **Gate results:** `./scripts/verify-ticket SELLO-109 --retain` passed G1 full on
  `1f000a6`: 1164 host tests in 104 suites, zero failures; lint 0 errors and 34 warnings,
  as on `main`. The whole app device suite passed 471/471 on
  `sello_e19b_api30` (API 30) on `696b70f` (the same code before the last `main` merge,
  which only brought SELLO-111's identical squash); the hosted check runs every device
  suite on the merge result.
- **Quality run:** run 20261025T231944Z-7d09f24e; SELLO-109 G1 passed; HEAD 1f000a6, inputs sha256 6caf6e5642e8, changes sha256 450e0045a3ec; report docs/tickets/E19-investments/SELLO-109/quality-report.json sha256 a4a9627ff365f6e710178903bbba231af440b47c5b03ce8c2cce4a400a0e63fe
- **Device / Artifact:** `sello_e19b_api30` (API 30 emulator); screenshots in SELLO-110's
  `artifacts/` (`tab-empty.png`, `overview.png`); no owner device.
- **Review:** executor self-review; owner review by merging SELLO-110's pull request.
