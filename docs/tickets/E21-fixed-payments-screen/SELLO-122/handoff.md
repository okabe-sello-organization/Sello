# SELLO-122 — Redesign Pagos e ingresos fijos

- What changed: Pagos e ingresos fijos leads with the month's total and a bar of what went through against what is still to come, then groups the schedules as Faltan, Ya se anotaron / Ya entraron, Más adelante, En pausa and Terminados, with the day in a pill. No month in the title, no Anotar button; the + adds a payment or an income by tab. `PlanSlip` text now reads in the dark theme.
- Where: `ScheduleListPolicy` (`recordedThisMonth`), `feature/recurrence/SchedulesViewModel.kt`, `SchedulesScreen.kt`, the schedules route in `navigation/SelloApp.kt`, `ScheduleRow` in `design-system/.../Recurrence.kt`, `PlanSlip` in `Plan.kt`, the catalog's `ScheduleRowExample` and the list paragraph of `docs/design/v2/README.md`.
- Still open: nothing for E21. `EntryCatalogInteractionTest`'s Nueva test fails on a pixel_5-sized API 30 AVD, outside this change.
