# SELLO-134 — Varios movimientos: several movements of one investment at once

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-129, SELLO-130
- **Gate:** G2

### Outcome
From an investment, the person types several movements, each with its type and date, and
saves them together.

### Acceptance criteria
- Opens from the entry points of the approved design for one investment.
- Each line has a type (the ones single entry offers), an amount in the investment's
  currency, a date never in the future and an optional note. A new line copies the type
  and date above it.
- A line refused by the investment rules is marked with its reason, and the draft stays.
- Saving, the unknown outcome, the confirmation and the restored draft behave as in
  SELLO-131, through SELLO-129's command. An investment that changed meanwhile asks to
  review and save again; it never overwrites.
- A USD investment's receipt shows dollars per line and its total in dollars; the COP
  view uses today's rate as elsewhere (E19).
- ViewModel JVM tests cover the types, a refused line, USD and every save outcome.
