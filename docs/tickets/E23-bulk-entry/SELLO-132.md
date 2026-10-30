# SELLO-132 — Anotar varios: several incomes at once

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-131
- **Gate:** G2

### Outcome
The bulk receipt's Gasto/Ingreso switch turns it into several incomes, saved together.

### Acceptance criteria
- The switch sets the kind of the whole receipt. Typed lines behave as the approved design
  says when it is switched; expense and income lines are never saved together.
- Lines take an income source; **Otro** asks for its name through the note composer,
  per line. The amounts show in gain green with a `+`, and the button reads **Anotar
  ingreso** or **Anotar 3 ingresos**.
- Opens from the income entry and from the income list, as the design draws.
- Saving, refusals, the unknown outcome, the confirmation and the restored draft behave
  as in SELLO-131, through SELLO-127's income command.
- ViewModel JVM tests cover the switch, the sources, Otro and every save outcome.
