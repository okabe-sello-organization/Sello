# SELLO-117 — Keep category amounts whole in Recibo's grid

- **Revision:** the gate tested `9e8e43e` on `sello-117-category-amounts`, branched from
  `main` at `b8a8510`, which had not moved. The tree was clean; the status line, this file,
  the handoff and the retained report are added afterwards.
- **Requirement mapping:** the amount stays whole in a 320dp grid column →
  `CategoryCellAmountTest.aSpentAmountStaysWholeInANarrowGridColumn` (a 120dp cell is as
  tall as a 320dp one, in all four schemes); the cell is still one button saying the whole
  category → `CategorySemanticsTest` (unchanged expectations); cells with a limit unchanged
  → `CategorySemanticsTest`, the Recibo device tests and `UiAcceptanceSweepTest`.
- **Red / Green:** with only the parameter renamed, the narrow cell was 147dp tall against
  48dp with room (the amount broken into lines); with the amount on its own line and the
  caption under it, both are equal.
- **Gate results:** `./scripts/verify-ticket SELLO-117 --retain` passed all six G1 checks on
  `9e8e43e`: 953 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, as on `main`. Device suites run in the hosted `quality` check. Local device
  runs on `sello115_api30`: the whole design-system suite, the `feature.recibo` package and
  `UiAcceptanceSweepTest`, all passed.
- **Quality run:** run 20261025T051644Z-dc4a1cd7; SELLO-117 G1 passed; HEAD 9e8e43e, inputs sha256 f3140f295a55, changes sha256 8a0b9bf4d9d6; report docs/tickets/E16-v2-today-gaps/SELLO-117/quality-report.json sha256 fb174d4f216c8bb93018fceca50e630f51d18894652846d382520cc0018fdd9b
- **Device / Artifact:** synthetic AVD `sello115_api30` (API 30, 320 × 640):
  `artifacts/past-month-320.png`, September with "120.000" whole and "gastado" under it.
- **Review:** executor self-review; owner review by merging the pull request.
