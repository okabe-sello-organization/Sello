# SELLO-092 — Earn stamps and complete goals

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-091, SELLO-004
- **Gate:** G2

### Outcome
Crossing 25/50/75 % earns a stamp with a modest celebration; reaching 100 % offers **Marcar como lograda**.

### Deliverables
Milestone detection, celebrations with reduced-motion equivalents, completion with a 10-second Deshacer.
Reuses `MilestonePlace` landing, `StampTicket`, `ReachedRow` and `CelebrationOverlay` ([gap list](../../design/v2/gaps/metas.md)); adds the kept stamp's relight and the 10-second Deshacer, longer with TalkBack.

### Acceptance criteria
- Stamps follow the approved examples, including faded stamps after a withdrawal or target change.
- Celebrations follow only a confirmed save.
- After the Deshacer window the goal is final and read-only.

### Tests
Domain and journey tests for each crossing, replay safeguards and reduced motion; G2.

### Working checkpoint
A goal can reach each stamp and be completed with its celebration.

### Context and starting points
v2 celebrations page.

### Implementation plan
1. Milestone detection.
2. Celebrations.
3. Completion and undo.

### Concrete cases and pitfalls
- Reopening a screen is not a new crossing.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-092 --retain`.

### Execution progress

- **Decisions taken while delivering (owner asked to continue, 2026-10-24):** the stamp
  lands once per confirmed save; reopening or redrawing the goal while the notice is said
  shows it already down. Deshacer lasts 10 seconds after the big moment, 30 with TalkBack.
  A target change that earns a stamp says "Meta actualizada" with the stamp line. The
  Metas ticket follows the v2 layout (stamps left, dashed tear, text right).
- `MilestonePlace` relights a kept stamp ("De vuelta en el 50 %"); `MilestoneRow` lands
  or relights several places; `StampTicket` takes the v2 horizontal layout.
- `GoalMovementViewModel` and `GoalEditorViewModel` build a `GoalCelebration` from the
  receipt's `GoalChange`, never from a replay or a save found after an unknown outcome.
- `GoalCompleter` sends one `CompleteGoal`, opens `LogradaMoment` only after the receipt
  and recovers an unknown outcome with its own operation; `ExpenseCorrections` offers
  Deshacer through `UndoGoalCompletion`. Marcar como lograda is on the goal page at 100 %,
  on the Metas card and in Editar meta ("¿Ya la lograste?", MT26).
