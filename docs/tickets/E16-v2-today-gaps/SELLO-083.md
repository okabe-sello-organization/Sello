# SELLO-083 — Suggest assigning unassigned income

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-079, SELLO-004
- **Gate:** G2

### Outcome
Income history shows the v2 suggestion while part of the month's income has no budget.

### Deliverables
The suggestion note with **Asignar a una categoría ›**, shown only for the current month with a positive unassigned amount.

### Acceptance criteria
- Shown only while unassigned is positive in the current month.
- Not shown for a closed month or a negative unassigned amount.
- The action opens category creation; nothing is assigned automatically.

### Tests
ViewModel and screen tests for each state; G2.

### Working checkpoint
With unassigned income, the suggestion appears and leads to a new category.

### Context and starting points
E13 SELLO-071 drew no suggestion. Design: "Income" in the v2 README.

### Implementation plan
1. State and visibility rule.
2. Note and action.
3. Tests.

### Concrete cases and pitfalls
- Goal or investment records never assign income.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-083 --retain`.
