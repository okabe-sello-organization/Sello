# SELLO-086 — Approve goal rules and worked examples

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-085, SELLO-004
- **Gate:** G1

### Outcome
Reviewed rules and independent worked examples for every goal operation, before any code.

### Deliverables
An ADR or rules document with examples: contribution, withdrawal (never above saved), stamps at 25/50/75 % and the goal, target edits, corrections with undo, past-dated movements, completion with Deshacer, deletion and Recibo's goal choice.

### Acceptance criteria
- Every decision in ADR 0017 and ADR 0013's Metas rules has at least one worked example with exact amounts.
- Milestone replay is defined: redrawing or reopening never earns a stamp again.
- The owner approves the examples; open points are listed, not assumed.

### Tests
Review of the examples by the owner; G1.

### Working checkpoint
The approved examples exist and later tickets cite them.

### Context and starting points
[ADR 0017](../../decisions/0017-goals-recurrence-investments.md); Metas brief; v2 "Metas".

### Implementation plan
1. Draft rules and examples.
2. Owner review.
3. Record approval.

### Concrete cases and pitfalls
- Examples are worked out independently, not from code.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-086 --retain`.

### Execution progress

- **Owner decisions, 2026-10-23:** a percentage may pass 100 %; a correction that would
  leave less than zero saved is refused and names the withdrawal that depends on it; a
  goal marked achieved below 100 % gets all four stamps; a stamp newly reached by
  lowering the target is earned with the modest celebration.
- [Goal rules and worked examples](../../testing/goal-examples.md): 14 rules and 38
  examples (MT01–MT38), each figure checked by hand; four further points are proposed
  there and approved by merging.
