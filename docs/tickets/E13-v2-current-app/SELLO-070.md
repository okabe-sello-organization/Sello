# SELLO-070 — Bring expense entry and edit to v2

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-066, SELLO-004
- **Gate:** G2

### Outcome
Anotar opens the v2 form: amount slip, two rows of category chips ending in
**Nueva**, a note row with its docked composer, a fixed **Anotar gasto** button and
the compact Sello pad.

### Deliverables
The expense form rebuilt on `V2EntryLayout`, `EntryAmountSlip`, `V2ChipRows`,
`SelloChip` with category icons, `AddChip`, `EntryLine`, `NoteComposer`, the compact
`AmountKeypad` and `V2Dock`-style action, for both new entries and edits, with its
screen tests updated.

### Acceptance criteria
- Order and behaviour follow the v2 README "Expense and income entry": no category
  preselected (except from "Anotar aquí"), at most two chip rows scrolling sideways,
  the amount, action and pad never moving.
- **Nueva** opens the category editor and returns with the new category selected, as
  today's entry prerequisite does.
- The note row opens the composer docked to the keyboard; over 60 characters the
  text is kept, the counter turns red and ✓ is disabled; editing starts with the
  cursor at the end.
- Amount sizes step down as digits are added; a 13th digit shows **Máximo 12 dígitos**
  in place of the label without moving anything.
- Saving, unknown-outcome recovery ("Comprobar si se guardó"), drafts restored after
  process death, past-month edits and the edit-only delete keep today's rules.
- Short windows scroll the amount with the choices; two-column windows keep today's
  split; large text and 320dp windows keep every control reachable.

### Tests
Updated expense entry screen and journey tests (new entry, edit, unknown outcome,
restoration), the keyboard-inset test and G2.

### Working checkpoint
On a phone, an expense can be entered, edited and recovered after process death in
the v2 form, and the saved confirmation from SELLO-066 appears.

### Context and starting points
Today: `app/.../feature/expense/ExpenseEntryScreen.kt` and its root, render model and
tests; see "6. Expense entry and edit" in the current app inventory. Reference:
`docs/design/v2/entry.html` and the catalog example `v2.entry` in
`catalog/.../v2/V2InputExamples.kt`, which already shows every state with synthetic
data.

### Implementation plan
1. Rebuild the form's layout on `V2EntryLayout` with the v2 pieces.
2. Wire **Nueva** to the existing category-creation route.
3. Move the note to `EntryLine` and `NoteComposer`.
4. Update screen and journey tests; check 320dp, font 2.0 and two columns.

### Concrete cases and pitfalls
- The composer's keyboard must not resize or flip the form behind it.
- Edits have no Gasto/Ingreso switch, as today.
- The note limit counts characters as people see them (code points), not UTF-16 units.

### Verification recipe
`:app` focused screen and journey tests, then `./scripts/verify-ticket SELLO-070 --retain`.
