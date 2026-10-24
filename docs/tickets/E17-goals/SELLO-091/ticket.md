# SELLO-091 — Record contributions and withdrawals

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-089, SELLO-004
- **Gate:** G2

### Outcome
Aportar and Retirar record movements, with past dates and corrections.

### Deliverables
The Aportar/Retirar form with the Sello pad and date pill, the movement history with edit, delete and undo.
Reuses `EntryLayout`, `SegmentedSwitch`, `EntryAmountSlip` (Gain / Ink), `FactLine` and `FieldPill` ([gap list](../../../design/v2/gaps/metas.md)); extends `SelloChip` with the goal's percentage.

### Acceptance criteria
- A withdrawal above saved is refused with a clear message.
- Past dates allowed, future refused.
- Unknown outcomes are recovered before another write.

### Tests
ViewModel, screen and journey tests; duplicate-submit and unknown-outcome cases; G2.

### Working checkpoint
Movements recorded, edited and undone update the goal exactly.

### Context and starting points
v2 Aportar page.

### Implementation plan
1. Form.
2. History with corrections.
3. Tests.

### Concrete cases and pitfalls
- Contributions never touch category budgets.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-091 --retain`.

### Execution progress

- **Decisions taken while delivering (owner asked to continue, 2026-10-24):** the form
  closes back where it was opened and says "Aporte anotado: …" or "Retiro anotado: …";
  a correction keeps the movement's goal and kind, so the switch and the chips are
  fixed while correcting; deleting a movement uses the one shared Deshacer, as for
  expenses and income. Stamp celebrations after an aporte are SELLO-092's.
- `GoalPolicy.withMovement` checks a new or corrected movement against the goal's
  history before it is sent, by the commands' own rules: a retiro above what is saved
  (MT08) and a correction or deletion a later retiro needs (MT15, MT16) wait with the
  reason.
- `SelloChip` takes a softer trailing text (the goal's percentage).
- `ExpenseCorrections` offers the shared Deshacer for goal movements too, in their own
  words, through `GoalCommands`.
- `GoalMovementRoute`, `GoalMovementViewModel` and `GoalMovementScreen`: Aportar / Retirar
  on the entry layout with the Sello pad, the date pill (past only), goal chips with their
  percentage, "Llevas … guardados en esta meta." and the note; the correction form with
  Borrar. Metas' **Aportar a una meta**, the goal's **Aportar a esta meta**, **Retirar
  dinero** and its movement rows now open it.
