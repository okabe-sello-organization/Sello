# SELLO-017 — Ship Anotar expense and its durable receipt end-to-end

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-012, SELLO-014, SELLO-016, SELLO-007, SELLO-008, SELLO-004
- **Gate:** G2

### Outcome
Complete the first money-writing UI path, not an attractive form with a fake save.

### Deliverables
Amount-first Anotar sheet with COP/category/date/note, preview from shared policies,
State/Action/Event/ViewModel/Root/Screen, persisted draft/operation ID, receipt display
and recovery/retry affordances. MVP has no recurrence/batch toggle or dormant routes.

### Acceptance criteria
- Valid confirmation saves exactly one expense and shows Recibido only for its
  confirmed create receipt. Recomposition, navigation and restart never re-save.
- Invalid/future input rejects clearly. Pending submit disables duplicate action;
  unknown outcome recovers using the original ID and preserves draft if rejected.
- Dismiss/rotation/system keyboard follows reference and accessibility contract.
  Success haptics/animation cannot affect whether financial save succeeded.

### Tests
ViewModel submit/cancellation/error/restored-ID tests; real-Room UI duplicate/restart
journeys; exact amount/category/date audit and accessibility assertions. Run G2.

### Working checkpoint
Fresh install → category → Anotar → receipt → persisted expense works on device;
later app startup reads the same record. No developer fixtures are required.

### Context and starting points
This is the first user-visible financial commit; visual save completion is not
financial proof. Inputs are expense command/lookup from SELLO-012, preview policy
SELLO-014, categories from SELLO-016 and controls. Read
[Anotar](../../design/sello-spec.html#s-anotar), architecture §§5/6 and recovery cases.

### Implementation plan
1. Build expense feature/form/receipt rendering with amount/category/date/note draft;
   default category/date through explicit rules and prefill validated caller context.
2. Derive “after save” preview from the shared snapshot/policy with draft inputs;
   label it as preview, never persist forecast or recompute rules inside the screen.
3. Generate one operation ID per logical submission, save it before invoking the
   command, and make submit/recovery/busy/error states explicit in the ViewModel.
4. On committed result read/display the actual durable receipt and refresh observed
   Recibo; recover unknown/canceled submits by original ID after recreation.
5. Implement dismiss/keyboard/back/accessibility and exact success animation. Do not
   surface recurrence/batch controls or creation celebration for an edit.

### Concrete cases and pitfalls
- Enter 10,000/category C/today, tap twice: one row and receipt. Reopen/recompose
  receipt: no command invocation. Changing draft after rejected submit needs a new logical ID.
- If preview becomes stale while another command commits, final receipt/snapshot
  wins; never announce the preview as saved truth.
- Interrupted save preserves pending identity; UI must not tell the user to blindly
  repeat the same money under a new ID.

### Verification recipe
Create/run `*ExpenseFormViewModelTest` plus `ExpenseEntryJourneyTest` on real Room/UI;
G2. Execute create/repeat-tap/recreate/eligible-process-death/read-back journeys and
compare exact row/category/date/receipt IDs, not just snackbar text.

### Execution progress
2026-10-10: started on branch `sello-017-anotar-expense` after SELLO-016 merged. No
dependency was added and the schema did not change.

Delivered. `:domain`: `ExpensePreviewPolicy` and the `ExpenseReads` port. `:data`:
`RoomExpenseReads`. `:app`: the Anotar form (state, actions, view model, root and
screen) on the real `CreateExpense` command, with its draft, the identifier of a
save in flight and the saved expense's identifier kept across rotation and process
death; the receipt read back from storage; the dock on Recibo; the route; and the
`sello://anotar` link declared in the manifest. `docs/development/expense-entry.md`
states the rules and contracts.

Choices made during the work, for owner review:
- After saving, the screen shows a receipt with the stored expense and "Recibido",
  with a "Listo" button, as the ticket asks. The reference instead closes the sheet
  and shows a "Gasto anotado" message with "Deshacer"; undo belongs to SELLO-019.
- The category that starts selected: the one a link or a just-created category asks
  for; otherwise the one used last; otherwise the only one. With several and no
  history, none, and the person chooses.
- The date is the day the form was opened and stays that day if the draft is
  restored later. Earlier days can be picked with the platform's date picker in
  Sello's colours; later days cannot.
- The preview is labelled "Vista previa, si lo anotas" and says "te pasas por" with
  the amount when a limit would be passed. Going over never blocks saving.
- The keypad and the button stay at the bottom while the rest scrolls. The first
  version scrolled everything and the button ended up below the screen.
- Anotar is a full screen, not a sheet rising from the dock, and has no print-feed
  motion yet. The stamp lands and the phone vibrates once per receipt.
- The link is declared for apps on the device only, not for web pages.
- The route carries the prefilled category and amount as hints. They are drafts
  from outside, checked again by the form; nothing saved travels in a route.
- No Gasto/Ingreso switch, "varios" or repeat control: those features do not exist.

Found on the way: on the first manual run the "Anotar gasto" button was below the
visible screen, behind the system bar, once the preview appeared. The layout was
changed so the keypad and button are always in view. Two test faults were also
fixed: a click aimed by screen position missed while the keyboard was sliding in,
and a keyboard left open by one test held the focus the next one needed.

Left for later: undo, edit and delete (SELLO-019); income (SELLO-021); the real
Recibo content and expense history (SELLO-018); the sandbox scenarios (SELLO-024).
