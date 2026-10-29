# SELLO-080 — Lead Resumen with the same-days comparison

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
Resumen opens with "¿Cómo va este mes?": the difference with the same days of the previous month, and for a past month "¿Cómo cerró …?" comparing full months.

### Deliverables
The comparison worked out in domain from recorded expenses, the v2 hero with two bars on one scale and exact amounts, replacing the kept daily chart; first-month and failed-read states.

### Acceptance criteria
- Up and down use the same neutral ink and arrow; the percentage is exact to one decimal.
- The month in progress compares day 1 to today with the same days of last month; a past month compares whole months.
- A first month says what is missing and shows no comparison; a failed read is never zero.
- "Así crecen tus gastos" is removed only once the hero replaces it.

### Tests
Domain tests with hand-worked examples (month ends, 31 vs 30 days, February); screen tests; G2.

### Working checkpoint
On a phone, Resumen's first slip answers how this month compares, with exact amounts.

### Context and starting points
E13 SELLO-072 left the hero out; `ComparisonSlip` exists in the catalog. Design: "Resumen" in the v2 README.

### Implementation plan
1. Write worked examples and the domain calculation.
2. Wire the hero and states.
3. Remove the daily chart.

### Concrete cases and pitfalls
- Same days means calendar days 1..today, clamped when last month is shorter.
- Never project the month in progress.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-080 --retain`.
