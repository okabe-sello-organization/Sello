# SELLO-123 — Show where the income went on Resumen

- What changed: Resumen's income slip answers "¿A dónde fue lo que entró?": Entró, a spent/rest bar, the three categories that took most taken off, the others together and Ingresos menos gastos, then a stub to the income list ("3 en octubre") and Pagos e ingresos fijos ("Próximo: 28 oct"). Sin asignar left Resumen; it stays on the income screen.
- Where: `ResumenPolicy`/`ResumenSnapshot` (`incomeCount`, `IncomeDestinations`), `ScheduleListPolicy.nextDate`, `feature/resumen/` (state, render model, view model, `IncomeSlip`), `SlipStub.kt` in the design system, the catalog's income example and the v2 Resumen paragraph.
- Still open: nothing for E22. Spending only by owner decision: goals and investments are not shown as destinations (ADR 0012 BG4). `IncomeJourneyTest`'s source chip fails at a 320x640 screen on `main` too, outside this change.
