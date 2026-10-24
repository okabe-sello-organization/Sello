# SELLO-090 — Create, edit and delete goals

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-089, SELLO-004
- **Gate:** G2

### Outcome
A person can create a goal with the Sello pad, edit it and delete it with confirmation.

### Deliverables
The create/edit screen with projected months only while editing, and deletion with a confirm slip.
Reuses `IconChoiceStrip`, `FormField`, `AmountKeypad`, `MilestoneSpec` captions, `DangerSlip` and `ConfirmSlip` ([gap list](../../../design/v2/gaps/metas.md)); adds the large icon circle beside the name and the pad that slides up over the form.

### Acceptance criteria
- Target amount uses the Sello pad; target date optional.
- Projected months state their assumed pace and use whole pesos.
- A completed goal can only be deleted.

### Tests
ViewModel and screen tests including process-death restoration; G2.

### Working checkpoint
A goal can be created, edited and deleted on a phone.

### Context and starting points
v2 create page; approved examples.

### Implementation plan
1. Create and edit.
2. Projection display.
3. Deletion.

### Concrete cases and pitfalls
- Never promise a completion date.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-090 --retain`.

### Execution progress

- **Decisions taken while delivering (owner asked to continue, 2026-10-24):** after
  Crear meta the form returns to Metas, after Guardar cambios to the goal, after Borrar
  to Metas; leaving with changes asks first, as the category editor does; a target month
  can be chosen from next month up to 50 years ahead. **¿Ya la lograste? / Marcar como
  lograda** is completion, delivered with its Deshacer in SELLO-092, so the edit form
  does not show it yet.
- `GoalPolicy.withTarget` previews the goal under a new target while editing, so earned
  stamps show "logrado" (kept ones too, rule 7) and the pace counts only what is missing.
- Design system: `GoalIconCircle` (the large icon circle, dashed before an icon is
  chosen) and `SlidingKeypad` (the Sello pad sliding up with Listo; reduced motion shows
  it without moving).
- `GoalEditorRoute`, `GoalEditorViewModel` and `GoalEditorScreen`: name (40 characters,
  counter, text kept when too long), the ten goal icons, the amount on the pad, Para
  cuándo, the plan and the stamps with projected months, Borrar esta meta with the
  confirm slip; a completed goal offers only deletion. SELLO-089's **+**, **Crear una
  meta** and edit pencil now open it.
