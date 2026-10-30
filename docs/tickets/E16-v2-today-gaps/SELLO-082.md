# SELLO-082 — Offer one budget suggestion in Resumen

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
Resumen shows at most one tinted suggestion when a category went over its limit two months in a row.

### Deliverables
The eligibility rule in domain, the v2 suggestion note with its one action (Ajustar el presupuesto de …), dismissal kept per month.

### Acceptance criteria
- Eligible: over its limit in each of the last two full months; the bigger budget wins a tie.
- The evidence is a plain fact and the advice starts with "Podrías…" and ends in one action.
- Dismissing hides it for that month; it may return next month if still true.
- No suggestion in a first month or for a closed month without the two-month evidence.

### Tests
Domain tests for eligibility and ties; dismissal persistence; screen test; G2.

### Working checkpoint
A category over twice in a row shows the suggestion; dismissing it hides it until next month.

### Context and starting points
Rules in the Resumen brief; return-after-dismissal in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md).

### Implementation plan
1. Eligibility in domain with examples.
2. Note, action and dismissal storage.
3. Tests.

### Concrete cases and pitfalls
- The action opens the category's limit editor; it never changes a budget by itself.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-082 --retain`.
