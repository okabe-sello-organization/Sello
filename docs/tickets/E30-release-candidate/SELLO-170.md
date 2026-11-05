# SELLO-170 — Reintentar reads again and stale figures keep every form usable

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
After a failed first read, Reintentar reads again on every form. A failed refresh after
something loaded keeps the form usable on the last good figures, marked as older.

### Acceptance criteria
- Aportar/Retirar, Cambiar la tasa and the investment editor (Nueva inversión and its
  edit): when the first read fails, Reintentar subscribes to the board again, and the
  form loads when the second read succeeds.
- Anotar varios and the schedule editor: a `Failed` read with last good data keeps the
  category chooser and the lines and shows the stale notice. Guardar stays possible
  whenever the form has what the command needs. The error screen appears only when
  nothing ever loaded.
- Changing Anotar varios' date to a month never read shows the load error only for that
  month, with Reintentar.
- Red-first JVM tests use a fake whose first read is `Failed(…, null)` and whose next
  subscription is `Ready`. A second fake emits `Failed(lastGood)` the way `ObservedReads`
  does.

### Notes
Audit findings P1 and P2 (2026-11-04): `GoalMovementViewModel.kt:107, 204-210`,
`UsdRateEntryViewModel.kt:74, 135-141`, `InvestmentEditorViewModel.kt:112, 226-230`,
`BulkEntryViewModel.kt:488-490, 758`, `ScheduleEditorViewModel.kt:421, 442, 540`.
Same bug class as #285. Copy the retry pattern of `InvestmentMovementViewModel.kt:154-155,
307` (SELLO-157), and reuse `LastGoodRead` as `ExpenseEntryViewModel.kt:224` does
(SELLO-151). Review: lighter.
