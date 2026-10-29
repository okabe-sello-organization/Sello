# SELLO-081 — Show the average month and the weekday answer in Resumen

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
Mes a mes starts with "Un mes normal" and Resumen answers "¿Qué días gastas más?".

### Deliverables
The average of the full months shown and the per-weekday average over the last six months (the selected one included), from domain, with their v2 slips and accessible exact values.

### Acceptance criteria
- "Un mes normal" averages only the full months shown, in whole pesos, rounding stated in its examples.
- The weekday answer names the day first ("Los sábados") with its average, then the weekday columns, over the last six months or from the first recorded day, saying which.
- Months or weekdays without data are said, never shown as zero spending.

### Tests
Domain tests with hand-worked averages; screen tests; G2.

### Working checkpoint
Both answers appear with exact figures for the selected month.

### Context and starting points
Weekday period decided in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md). Design: "Resumen" in the v2 README.

### Implementation plan
1. Worked examples.
2. Domain calculations.
3. Slips and tests.

### Concrete cases and pitfalls
- Average per weekday divides by how many of that weekday the window has had, not by recorded days.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-081 --retain`.
