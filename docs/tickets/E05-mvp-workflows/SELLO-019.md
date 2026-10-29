# SELLO-019 — Implement expense edits, deletion and guarded six-second undo

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-012, SELLO-017, SELLO-018, SELLO-004
- **Gate:** G2

### Outcome
Users can correct/remove a recorded expense without false success or unsafe restoration.

### Deliverables
Versioned expense edit/delete/compensation commands and real transactional adapters;
durable deletion receipt/tombstone as needed; editor and menu/swipe equivalents;
six-second undo offer tied to a real monotonic timer, never simulated financial time.

### Acceptance criteria
- Edit/delete proves affected row/version and updates consistent snapshots. Editing
  a deleted/stale record conflicts instead of reporting success; edit has no creation stamp.
- Undo restores only the original eligible deletion and refuses later conflicting
  edits/recreation/reset. Restart/recomposition cannot extend the original undo offer.
- Apply approved D07/U01–U05: deadline is six real elapsed seconds; rotation and
  backgrounding within the live process preserve it. Process death expires the
  interactive offer, but deletion/compensation receipts still recover actual outcomes.
- Same operation replays safely; failure/cancellation preserves recoverable outcome.
  Both gesture and accessible menu paths exist, and deadlines/receipts are explicit.
- Historical expense correction is discoverable through contextual guidance and
  retained help; effective date/affected period is explicit before saving and
  completion feedback follows the committed receipt (D04/H06).

### Tests
Real-Room stale-edit/delete/undo races, rollback, missing row and reset-generation
tests; virtual monotonic expiry and rotated/recreated UI tests; device history totals
before/after edit/delete/undo. Extend catalog states and sandbox regression inputs.
Include exact deadline, same-process recreation, process-death offer expiry and
already-committed compensation recovery without re-deleting the restored record.

### Working checkpoint
Existing create journey still works; corrections survive restart and never silently
overwrite newer history. Run G2.

### Context and starting points
An expense is historical financial data, so an unchecked update or blind undo is
not a harmless form action. Use SELLO-012's operation protocol, SELLO-017's form,
SELLO-018's lists and approved D07. Read architecture §5 and
[recovery cases](../../testing/mvp-financial-examples.md#recovery-contracts-to-instantiate-in-downstream-tests--d07d09).

### Implementation plan
1. Add expense-specific edit/delete/compensate commands with operation ID, generation
   and expected row version. Check affected rows and increment revision atomically.
2. Persist the original deleted payload and guarded compensation identity/expiry
   proof according to D07; define missing/stale/conflict outcomes explicitly.
3. Open the existing form in edit mode from real history ID; load current version,
   preserve raw draft, display Guardar cambios, and never replay create on restoration.
4. Add accessible delete menu and swipe equivalent, immediate committed deletion
   and six-second Deshacer using monotonic time. Recreation cannot restart the timer.
5. Restore only if compensation's version/generation/eligibility still holds; update
   observed snapshots rather than manually editing rendered totals. Add conflict/error states.

### Concrete cases and pitfalls
- Change 10,000 to 12,000: one record, increased version and totals by 2,000.
  A second editor holding the old version must fail without overwriting 12,000.
- Delete/undo returns the exact original record; repeated undo is idempotent only
  for that operation. Newer recreation/edit/reset must block compensation.
- Advancing financial clock a year leaves the real undo duration unchanged. After
  process death, recover actual outcomes but expire the offer, never grant fresh time.
  A previously committed undo still restores the record after receipt recovery.

### Verification recipe
Create/run `ExpenseEditDeleteContractTest`, `UndoCompensationContractTest` on real
Room and `*ExpenseEditViewModelTest` on host; G2. Execute edit/stale/delete/undo/expiry/
recreate device journeys with exact row/version/receipt/snapshot assertions.

### Execution progress
2026-10-12: started on branch `sello-019-expense-corrections`. Delivered in two pull
requests, as agreed with the project owner; the ticket stays In Progress until the second.

**Decisions taken with the project owner before any code**

- A deleted expense is removed from `expense` and what it was is kept in a separate
  table, not marked as deleted in place, so no read or total can count it by mistake.
- The six seconds are counted on the monotonic clock and held in memory. A process
  that ends takes the offer with it and startup removes the kept copy.
- A second deletion makes the first one final: at most one deletion can be undone.
- An edit is refused when the expense changed since it was read. An expense may be
  edited while it stays in a category archived since, or be moved out of it, but
  never moved into an archived category.
- "Guardar cambios" is available only after a change.
- Wording: "Editar gasto", "Guardar cambios", "Borrar", "Gasto borrado", "Deshacer",
  "Gasto actualizado". The creation flow does not change.
- The dismissible first-time announcement of historical correction is deferred to
  SELLO-023, which owns remembered preferences; a note is on that ticket. This
  ticket delivers the guidance shown in context every time.

**First pull request: storage and commands, no screens**

- Schema version 2 with a tested migration: `deleted_expense`.
- `ExpenseCommands.edit`, `delete`, `restore` and `undoOffer`, with their receipts.
- Real-database contract tests for U01 to U05, stale versions, replays, concurrent
  commands, cancelled callers, rollback and the month's totals.
- Nothing in the app calls the new commands yet, so every existing journey is as it was.
- Verified with the ticket's G2 gate on an API 30 emulator, each time on the
  uncommitted tree that became the commit: run 20261012T190048Z-4eafa3e0 passed
  before the first commits, and run 20261012T200307Z-43c7e3f2 after the date rule
  changed. Its report is not retained
  here; the run that counts as Delivery evidence is the one made when the ticket
  is proposed Done.

**Choices made while implementing, for review**

- An edit that changes nothing is refused by the command (`Unchanged`) and writes no
  receipt, so the rule does not depend on the screen alone.
- The date rule applies only when an edit changes the date (project owner,
  2026-10-12, replacing my first choice of checking every edit). An expense already
  dated after today, possible only if the financial day moved backwards, can have
  its other fields corrected; it cannot be moved to another future date.
- Undo puts the row back without interpreting it, exactly as deletion kept it.
- A new expense's sequence follows the highest of the recorded and the kept one.
  Once a deletion is final its sequence can be used again.

**Second pull request: the screens**

- The entry form corrects a recorded expense: "Editar gasto", "Guardar cambios" only
  after a change, "Borrar" in the top bar, the months it touches named before saving.
- A category's list: press to correct; long press, swipe and screen-reader actions to
  delete; a line that always says how.
- "Gasto borrado" with "Deshacer" over the bottom of Recibo and of a category's detail,
  held by one object for the whole process.
- Design system: `SelloNotice`, `ActionLine` and a notice slot in the scaffold, with
  catalog examples `feedback.notice` and `paper.action-line`.

**Choices made while implementing the screens, for review**

- Deleting does not ask first, from the form or from a row: undo is the safeguard.
- A correction has no receipt screen. The form closes and the list says "Gasto
  actualizado".
- The form shows no preview while correcting: the preview adds the draft to the month,
  and a recorded expense is already counted in it.
- Once the expense is known to have changed or gone underneath the form, nothing more
  can be sent from it; the person leaves and opens it again.
- Texts that were not agreed beforehand: "Gasto restaurado", "Ya no se puede deshacer.
  El gasto quedó borrado.", "No se pudo deshacer. Inténtalo de nuevo.", "No se pudo
  deshacer porque tus datos cambiaron. El gasto quedó borrado.", "Aún no cambiaste
  nada.", "Corregir este gasto cambia las cifras de septiembre.", "Toca un gasto para
  corregirlo. Para borrarlo, deslízalo o mantenlo presionado.", "Seguir editando", and
  the reasons a correction or deletion was refused.
- A notice that needs no answer stays four seconds.
- The offer is shown on Recibo and a category's detail, not over the entry form.

**Not delivered here, and where it goes**

- Sandbox regression inputs: there is no sandbox until SELLO-024; a note is on that
  ticket.
- The dismissible first-time announcement: SELLO-023, as decided above.
- Undo after recording a new expense, which the reference also shows: not part of this
  ticket's acceptance.
