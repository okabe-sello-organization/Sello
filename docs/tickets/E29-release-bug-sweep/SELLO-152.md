# SELLO-152 — Editar meta always settles: unknown saves, deletes and split edits

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
Editar meta can't be left with an unresolved save, never locks on Borrar, saves every
part of an edit, and closes after deleting a goal wherever it was opened from.

### Acceptance criteria
- After an unknown outcome whose lookup fails, Back and Descartar keep the editor open
  and only "Comprobar de nuevo" (same operation ID) is offered, so a second Crear can't
  make a second goal.
- With a save unresolved, Borrar meta is not offered; the confirm slip never shows a
  spinner that Cancel, Back and Retry can't leave.
- Name and target changed together: the name commits, the target part fails. Guardar
  again sends only the target and saves it.
- Recibo goal pill → goal → ✎ → Borrar: the editor and the goal page close and Recibo
  shows; the confirm slip is cleared; no second `DeleteGoal` is sent.
- Red-first JVM tests; a navigation test for the Recibo path.

### Notes
Audit findings: `GoalEditorViewModel.kt:150-157, 161-166, 383-398, 403-424, 449`,
`GoalDestinations.kt:303-304`. Review: lighter.
