# SELLO-133 — Aportar varios: several movements of one goal at once

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-128, SELLO-130
- **Gate:** G2

### Outcome
From a goal, the person types several aportes and retiros, each with its date, and saves
them together.

### Acceptance criteria
- Opens from the entry points of the approved design for one goal.
- Each line has a kind (Aportar or Retirar), an amount, a date never in the future and an
  optional note. A new line copies the kind and date above it.
- A refused retiro is marked on its line with the amount available, and the draft stays.
- Saving, the unknown outcome, the confirmation and the restored draft behave as in
  SELLO-131, through SELLO-128's command. A goal that changed meanwhile asks to review
  and save again; it never overwrites.
- A milestone or 100 % reached by the receipt celebrates once, after the commit.
- ViewModel JVM tests cover the lines, the refused retiro, the celebration and every save
  outcome.
