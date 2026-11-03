# SELLO-155 — Recibo, category detail and Resumen keep the last good figures

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
Reintentar, coming back to a screen, or a failed refresh never replaces figures already
shown with a skeleton or an error screen; they stay, marked as older, with a notice.

### Acceptance criteria
- Recibo, category detail, Resumen and Ajustar presupuesto: on a stale screen,
  Reintentar keeps the figures; if the read fails again they stay older with the
  notice. The full error screen appears only when nothing ever loaded.
- Returning after more than 5 seconds shows no skeleton, and Resumen keeps its scroll.
- The full expense list: a failed refresh keeps its rows, and Reintentar reads again.
- Pagos previstos and the limit sheet keep their last good list and facts with the
  notice; nothing silently disappears.
- JVM tests with a fake whose every new subscription starts from Loading with no last
  good value, as `ObservedReads.observeOn` does (`HandSnapshots` hides this today).

### Notes
Audit findings: `ObservedReads.kt:23-25` keeps last good per subscription;
`ReciboViewModel.kt:108-115, 144`, `CategoryDetailViewModel.kt:114, 180`,
`ResumenViewModel.kt:46, 100, 109` (`WhileSubscribed(5_000, 0)`),
`AdjustBudgetViewModel.kt:51-52, 58`, `CategoryExpensesViewModel.kt:100-102, 118, 139-147`,
`UpcomingPayments.kt:108`, `LimitSheetViewModel.kt:188-191`. A shared helper in `:app`
`presentation` is the planner's call; SELLO-157 reuses it. Review: lighter.
