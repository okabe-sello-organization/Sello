# SELLO-084 — Invite a look at the month just closed

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
In the first week of a month, Recibo shows one dashed line "Ver cómo cerraste octubre ›".

### Deliverables
The invitation with its timing and dismissal, opening Resumen for the previous month.

### Acceptance criteria
- Shown on days 1–7 when the previous month had expenses; one line, never a stack.
- Gone for that month once opened or dismissed, kept across restarts.
- Not shown when viewing a past month.

### Tests
Clock-controlled ViewModel tests for days 1, 7 and 8; dismissal persistence; screen test; G2.

### Working checkpoint
On day 2 the line appears; opening it shows last month in Resumen and it stays gone.

### Context and starting points
Timing and dismissal decided in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md).

### Implementation plan
1. Timing rule with the financial clock.
2. Line, navigation and dismissal.
3. Tests.

### Concrete cases and pitfalls
- Use the financial day, not the device clock directly.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-084 --retain`.
