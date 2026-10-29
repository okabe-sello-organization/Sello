# SELLO-115 — Finish what E17 left open

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-094, SELLO-004
- **Gate:** G2

### Outcome
Two stamps earned at once land one after the other; the sandbox replays a Deshacer too late; Recibo's first use shows the goal; the catalog shows the Metas dock and the goals band.

### Deliverables
- `MilestonePlace` waits before landing; `MilestoneRow` and `StampTicket` land each earned stamp 160 ms after the one before, as in the v2 celebrations page; reduced motion shows them at once.
- A Marcar step with its own Deshacer window and the scenario `metas-tarde` for MT25.
- Recibo's first-use screen draws the goal under its card.
- Catalog examples for the scaffold dock in Metas' green flag tone and for `GoalsBandTotal`, with and without aportes this month.

### Acceptance criteria
- With two stamps landing, the second is not drawn yet while the first lands, and both are drawn after the landing; with reduced motion both are drawn at once.
- `metas-tarde` refuses the Deshacer as `UndoExpired` and checks the goal completed, fresh, resumed and restored.
- With no categories and an active goal, Recibo shows the goal; with no active goal, nothing is added.
- The catalog lists the new examples with previews in the four themes.

### Tests
Device tests of the stagger and reduced motion; scenario definition and corpus tests; Recibo screen and journey tests; catalog example tests; G2.

### Working checkpoint
Earning 25 % and 50 % in one aporte from Metas shows the two stamps landing in turn.

### Context and starting points
Left open by E17 and listed on 2026-10-25; owner decisions that day: a new E17 ticket, Recibo's first use shows the goal (rule 12 only hides it with no active goal or on a past month). v2 `celebrate.js` (`260 + i * 160` ms), the SELLO-089 handoff.

### Implementation plan
1. Red tests for the stagger, the first-use goal and the catalog list.
2. The stagger, the first-use slot, the catalog examples.
3. The Marcar window step and `metas-tarde`.

### Concrete cases and pitfalls
- A zero Deshacer window is real elapsed time, not a sleep or an accelerated clock (architecture §9).

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-115 --retain`.
