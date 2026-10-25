# SELLO-102 — Show expected income in Plan del mes

Income shows Plan del mes for the month in progress while an income schedule still has an occurrence to come: Anotado, Esperado, Planeado, Sin asignar en el plan (BG3) and one chip per expected income.
`IncomePlanPolicy` (domain) works it out; `IncomeHistoryViewModel.plan` joins the month's summary and the schedules only at the same revision, so expected money is never also recorded.
Design system: `PlanSlip`, `ExpectedChip`, `LineEmphasis` and `TotalRule.Single`, in the catalog and `components.html`; `AddChip` shares the dashed outline.
Next: SELLO-104 (sandbox) can add a scenario that shows the plan; SELLO-114 (design pass) before SELLO-100, 101 and 103.
