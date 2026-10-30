# SELLO-016 — Deliver first-run category creation and amount-entry prerequisites

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-013, SELLO-015, SELLO-008, SELLO-004
- **Gate:** G2

### Outcome
A new user can create the first real category and optional monthly budget without fixtures.

### Deliverables
Category editor State/Action/Event/ViewModel/Root/Screen, name/icon/limit fields,
validated command submission and restored draft/operation ID. First-run Recibo
offers create category; backup action is exposed only after its real workflow exists.

### Acceptance criteria
- Anotar without an eligible category directs to the editor and explains the
  prerequisite. Save commits a real category/current-month limit and returns it selected.
- Invalid/duplicate submission is handled explicitly; archived identities cannot
  reappear as new eligible categories. Empty financial state is not an error fallback.
- Draft survives rotation/process reconstruction; creation stamp requires receipt.

### Tests
ViewModel action/draft/busy/receipt tests; real command/UI creation, invalid name/limit,
double submit and restart retention. Add editor-related component states to catalog.

### Working checkpoint
Fresh install can create/read its first category without test tools. G2 passes and
the next entry screen can use real category IDs.

### Context and starting points
The first category is a real prerequisite for expenses; seed data must not hide an
unusable fresh install. Use category/limit ports from SELLO-013 and controls from
SELLO-008. Read [category editor](../../design/sello-spec.html#s-catform) and
approved D04/D05; use those accepted rules for field bounds and month limits.

### Implementation plan
1. Add category feature State/Action/Event/ViewModel/Root/Screen and raw name/icon/
   optional-limit draft with required/error/busy states. Use approved lengths/keys.
2. Load eligible category state and implement first-run call-to-action; if none exist,
   the expense dock opens creation with a prerequisite explanation, not an empty form.
3. Submit create command with one preserved operation ID and generation. On uncertain
   outcome recover receipt; show creation state only after confirmed commit.
4. Return created category ID to the caller/selected context using the navigation
   contract, not a mutable category object or invented successful local row.
5. Save drafts across recreation and render commit/reference errors with retry; add
   any genuinely new shared control states to catalog rather than copying editor UI.

### Concrete cases and pitfalls
- Empty name/missing icon/invalid limit cannot create a row. Unlimited budget choice
  follows D04; empty text must not become zero without an explicit user selection.
- Double tap or process restart during save produces one category/period limit.
- Backup-import CTA is absent until SELLO-030 exists; a dead link is not onboarding.

### Verification recipe
Create/run `*CategoryEditorViewModelTest` and device `FirstCategoryJourneyTest`; G2.
On an empty **debug** profile create a category, restart, and verify its actual ID,
limit and selected state. Include raw-input/error and restored-operation assertions.

### Execution progress
2026-10-10: started on branch `sello-016-first-run-category` after SELLO-015 merged. No
dependency was added and the schema did not change.

Delivered. `:app`: the category editor (state, actions, view model, root and screen)
on the real `CreateCategory` command, with its draft, the identifier of a save in
flight and its result kept across rotation and process death; the first-run Recibo
with "Crear categorías"; Recibo listing categories with their limits and "Agregar
categoría"; the editor route with its returned identifier; and the "an entry needs a
category first" path. `:design-system`: `IconChoiceGrid` and nine more category
icons, with a catalog example `inputs.icon-choice`.
`docs/development/category-editor.md` states the form's rules and contracts.

Choices made during the work, for owner review:
- A new category is "Sin límite" unless the switch is turned off and an amount is
  typed. Turning it off and leaving the amount empty is an error, never zero.
- Fourteen icons, the ones the reference names. It shows twenty-eight without
  naming the other fourteen, so those need choosing. Each icon has a spoken name
  (Comida, Hogar, Transporte and so on).
- Recibo gained an "Agregar categoría" button under the list. The reference does
  not place one there, but without it only one category could ever be created
  until the editing ticket.
- The form is a full screen in every window. The reference makes editors a side
  panel on wide windows; that is left for later.
- Left out of the form for now: the preview of the category cell, the line saying
  what the month's total limit would become, and suggestions drawn from the
  person's own limits. The three fixed suggestions are shown.
- The explanation "to record an expense you first need a category" is delivered
  and tested, but today only an entry link triggers it, and the link is not yet
  registered with Android. The "Anotar" dock that will normally trigger it arrives
  with the entry form in SELLO-017, because a dock that opens nothing once a
  category exists would be a fake.
- Back while saving does nothing. Back with a draft asks before discarding.
- When the name is refused, the form scrolls back up to it.

Found on the way: on a small screen the "name already used" message appeared above
the visible area, because the button is at the bottom and the name at the top. The
form now scrolls to the name when it has an error.

Left for later: editing, archiving and limit changes (SELLO-020); the dock and the
entry form (SELLO-017); the real Recibo content (SELLO-018).
