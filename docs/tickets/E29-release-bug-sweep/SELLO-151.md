# SELLO-151 — Entry forms, income history and Pagos fijos keep what's on screen when a refresh fails

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-148, SELLO-150
- **Gate:** G1

### Outcome
A refresh that fails after something loaded keeps the typed draft or the list, marked as
older with a notice, instead of replacing it with an error screen.

### Acceptance criteria
- Expense and income forms: a `Failed` read with last good data keeps the draft
  editable and shows the stale notice; the error screen appears only when nothing ever
  loaded. Saving stays possible whenever the form has what the command needs.
- Pagos fijos and the income history split render their last good list or split,
  marked older, with retry.
- Income history's header total leaves out incomes dated after today, as Resumen does.
- JVM tests with a fake that emits `Failed(lastGood)` the way `ObservedReads` does.

### Notes
Audit findings: `ExpenseEntryViewModel.kt:903, 917-918`, `IncomeFormViewModel.kt:775-777`,
`SchedulesViewModel.kt:154-163`, `IncomeHistoryViewModel.kt:159`, `RoomIncomeReads.kt:82`.
ARCHITECTURE §6. Reuse the stale notice the reading screens already use. Review: lighter.
