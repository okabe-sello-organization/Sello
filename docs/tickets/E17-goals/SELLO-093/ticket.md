# SELLO-093 — Show one goal on Recibo

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-092, SELLO-004
- **Gate:** G2

### Outcome
Recibo shows the chosen goal as the v2 pill with Aportar, and a row when another goal reached 100 %.

### Deliverables
The goal pill, the person's choice of goal, the fallback rule and the green 100 % row.
Reuses `GoalPill` and `ReachedRow` ([gap list](../../../design/v2/gaps/metas.md)).

### Acceptance criteria
- The person's choice wins; otherwise the highest completion; nothing with no active goal.
- When the shown goal is completed or deleted, the fallback applies.
- Not shown on a past month.

### Tests
ViewModel and screen tests for each rule; G2.

### Working checkpoint
Recibo shows the right goal in each case.

### Context and starting points
Fallback decided in [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md); v2 Recibo.

### Implementation plan
1. Choice storage.
2. Pill and row.
3. Tests.

### Concrete cases and pitfalls
- The pill is the progress bar; keep its exact percentage accessible.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-093 --retain`.

### Execution progress

- **Decisions taken while delivering (owner approved the opening, 2026-10-24):** the choice
  and the fallback already lived in storage (`featured_goal`, `FeatureGoal`,
  `GoalPolicy.featured`), so no schema or domain change. The goal area is its own view
  model in `feature/goals`, drawn in a slot of Recibo, because features may not reference
  each other and Recibo's answer must not wait on goals. Recibo's first-use state shows no
  goal, as in v2. A failed goal read is said ("No pudimos leer tus metas") or keeps the last
  good goal; it is never shown as no goal.
- `ReciboGoalViewModel` shows `GoalBoard.featured` on the current month only, and the first
  other active goal at 100 % in Metas' order; Marcar from its row reuses `GoalCompleter`.
- `GoalPill` follows v2 (a filled round +, a brand edge at the fill, the name ellipsised)
  and says "Viaje a la costa, 57 por ciento. Ver meta" with its exact progress.
  `ReachedRow` is the v2 green pill with its compact button, stacked where it does not fit.
- The pill opens the goal and its + opens Aportar for it; Recibo's notice shows the stamp
  ticket, as on Metas.
