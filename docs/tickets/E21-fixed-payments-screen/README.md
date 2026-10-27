# SELLO-E21 — Pagos e ingresos fijos, redesigned

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** Pagos e ingresos fijos answers at a glance what the month's fixed payments or incomes add up to, what already went through and what is still to come.
- **Exit:** The screen leads with the month's total and how much of it was recorded, lists what is still due, what went through, what comes later and what is paused, reads well in both themes, and has no Anotar button.

**Owner direction (2026-10-26):** the owner reviewed the screen on the phone and approved a
redesign mockup (kept in SELLO-122's `artifacts/`), which replaces the list part of the
[v2 page](../../design/v2/fijos.html) locked by delegation in SELLO-114, with these changes:

- The empty screen has no "Anotar algo que se repite" button. The + in the top bar stays.
- No stamp on the summary, also not when every income arrived.
- The title has no month: the screen is always about the current month.

**Owner direction (2026-10-26, later):** the + in the top bar was too hidden. From six
concepts the owner chose the tear-off stub on the month's receipt, with its empty state
("El talón del recibo", in SELLO-125's `artifacts/`); SELLO-125 builds it.
