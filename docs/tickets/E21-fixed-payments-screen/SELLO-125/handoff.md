# SELLO-125 — Add a fixed payment from the month's receipt

- What changed: Pagos e ingresos fijos' month receipt ends in a tear-off stub, **+ Nuevo pago fijo** / **Nuevo ingreso fijo**, opening the entry with Se repite on; with nothing repeating it shows the month at 0, "Aún no tienes pagos fijos" and the stub with examples. The top bar's + is gone.
- Where: `feature/recurrence/SchedulesScreen.kt` (`Summary`, `Nothing`, `Figures`), the schedules route in `navigation/SelloApp.kt`, the strings, the v2 list paragraph and HANDOFF.
- Still open: nothing for E21.
