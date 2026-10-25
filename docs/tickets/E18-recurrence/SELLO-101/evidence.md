# SELLO-101 — Show reservations on Recibo

- **Revision:** the gate tested commit `15590e5` on `sello-101-reservations`, which merges
  `main` at `6f50bc3` (SELLO-100 done, #133); the only uncommitted file was the retained
  report. The status, this file and the handoff are added after.
- **Requirement mapping:** headline = remaining − reserved → `ReservationPolicy` and
  `MonthlyBudgetPolicy` (`ReservationPolicyTest` RE25–RE34, RE40; BG-E02, E03, E06, E11),
  read in the month's transaction (`ReservedSnapshotTest`, real Room); Sin margen and
  Revisar exactly on their rows, Revisar before Excedido → `ReciboRenderModelTest` RE27–RE29
  and `ReciboScreenTest`; no line when nothing is reserved → RE31–RE34 and the screen
  test; the v2 order in every reserved state, app and catalog → `ReciboScreenTest` and
  *Recibo answer states* (`AnswerSlipExample`); each payment opens its schedule →
  `UpcomingPaymentsViewModelTest` and Pagos previstos; device tests for `Footnote` and the
  warn stamp ink → `ReservedPartsTest`; working checkpoint → the screen tests on an
  API-30 emulator.
- **Red / Green:** deliberate breaks, each failing the tests named: the daily figure from
  `R` instead of `M` (RE25, RE26, RE27, RE28), Sin margen treated as Revisar (RE27), the
  label never **Falta para tus pagos** (RE28, RE29 in `ReciboRenderModelTest`).
- **Tests:** host `ReservationPolicyTest` 9, `ReciboRenderModelTest` +4 (20),
  `UpcomingPaymentsViewModelTest` 2; device `ReservedSnapshotTest` (data, real Room),
  `ReservedPartsTest` 2 (design-system), `ReciboScreenTest` +3 (26).
- **Gate results:** `./scripts/verify-ticket SELLO-101 --retain` passed G1 on `15590e5`:
  1035 host tests, zero failures; lint 0 errors and 34 warnings, as on `main`. Device
  suites on `sello098_api30` (API 30) on `15590e5`: data 310/310, design-system 181/181,
  catalog 22/22, app 430/430. The hosted check runs G2 on the merge result.
- **Quality run:** run 20261025T145149Z-c3bfc0aa; SELLO-101 G1 passed; HEAD 15590e5, inputs sha256 81e40dd3ffac, changes sha256 7254120bef39; report docs/tickets/E18-recurrence/SELLO-101/quality-report.json sha256 4acf0a8be8086f6193ffaf1636f5aca8552d8893f9672535b31829b8d6e3d076
- **Device / Artifact:** `sello098_api30` (API 30); no owner device.
- **Review:** executor self-review; the owner chose the RE29 wording on 2026-10-25; owner
  review by merging.
