# SELLO-157 — Metas and Inversiones keep the last good figures

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-155
- **Gate:** G1

### Outcome
Goal and investment screens keep figures already shown on Reintentar, on return and on a
failed refresh; Retry always reads again; Marcar como lograda works on older figures.

### Acceptance criteria
- Metas, the goal page, Recibo's goal carousel, Inversiones, the investment page and
  the dollar rate: the SELLO-155 behaviour (no skeleton or error screen over figures
  already shown; error only when nothing ever loaded).
- Anotar movimiento's (and Varios movimientos') "Vuelve a intentarlo" after a failed
  first read reads the board again (closes #285).
- Marcar como lograda on an older board still sends, with the version it shows; storage
  refuses a stale one as today. Never a silent no-op.
- JVM tests with a fake whose every new subscription starts from Loading.

### Notes
Audit findings: `MetasViewModel.kt:43, 68, 75`, `GoalDetailViewModel.kt:66, 101`,
`ReciboGoalViewModel.kt:50, 87, 91`, `InversionesViewModel.kt:34, 57`,
`InvestmentDetailViewModel.kt:35, 56`, `UsdRateViewModel.kt:30, 62`;
`InvestmentMovementViewModel.kt:294-300` for #285. Reuse SELLO-155's helper. Review: lighter.
