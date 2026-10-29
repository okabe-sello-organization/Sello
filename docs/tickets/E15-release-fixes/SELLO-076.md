# SELLO-076 — Show Resumen's "recorded until" date in words

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
Resumen's income slip says "Movimientos anotados hasta el 23 oct 2026" (or the app's
date form), not "Movimientos anotados hasta 2026-10-23".

### Deliverables
The date formatted like the app's other dates, in the render model, with a test.

### Acceptance criteria
- The line uses the app's es-CO date form, as the entry and category detail do.
- The spoken text matches what is shown.
- The date itself, the financial day it comes from, is unchanged.

### Tests
`ResumenRenderModelTest` or `ResumenScreenTest` asserting the formatted date; G2.

### Working checkpoint
Resumen shows the date in words for the current month and a past month.

### Context and starting points
Found in SELLO-035. `ResumenRenderModel` sets `asOf = budget.asOf.toString()`, and
`ResumenScreen` places it in `resumen_actual_through`.

### Implementation plan
1. Format `asOf` with the presentation date formatter in the render model.
2. Add the test.

### Concrete cases and pitfalls
- Format in presentation, not in the screen; keep the financial day from the snapshot.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-076 --retain`.

### Execution progress

- **Overlap, 2026-10-23:** SELLO-080–082 (#95) already changed the line to "hasta 3 de
  octubre", the form of Resumen's new weekday line. The owner chose to keep that long
  form, add "el" and show the year only for a day from another year: "Movimientos
  anotados hasta el 3 de octubre", "… hasta el 31 de diciembre de 2026" seen in 2027.
  The "could not refresh" line uses the same form.
- `ResumenRenderModel` formats `asOf` against the current financial month, which
  `ResumenViewModel` takes from the month session. The financial day itself is
  unchanged: a past month still says up to which day it was read.
