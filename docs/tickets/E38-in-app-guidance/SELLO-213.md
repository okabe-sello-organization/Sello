# SELLO-213 — Nudges for investments and the goal on Recibo

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-211
- **Gate:** G2

### Outcome
The person learns, once and when it matters, how to keep an investment up to date and
how to choose which goal Recibo shows.

### Acceptance criteria
- Each nudge is a nudge row (no pill) with a ✕, follows SELLO-211's rules and is not
  shown to people already using Sello when the update landed.
- **Investments**: in investment detail, the first time it opens after the first
  investment is created. Text says money put in or taken out is a movement, and that the
  value is updated by hand when it changes. Recording a movement or updating the value
  closes it.
- **Goal on Recibo**: on Recibo's goal card, once the person has two or more active
  goals. Text says how to choose the goals Recibo shows (as the code does it today).
  Changing that choice closes it.
- Each text is checked against the code and stays one or two lines.
- ViewModel tests for each trigger and its "closes" rule; one screenshot per nudge in
  light.

### Notes
- Lighter review. The Recibo goal carousel is E28; the choice lives in Metas
  (`GoalStarSheet`).
- Whichever ticket of the epic merges last sets the epic Done and refreshes
  [HANDOFF](../../development/HANDOFF.md).
