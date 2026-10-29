# SELLO-021 — Ship income recording, history and guarded correction workflows

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-012, SELLO-015, SELLO-008, SELLO-019, SELLO-004
- **Gate:** G2

### Outcome
Income is a complete real workflow, not a summary number populated by fixtures.

### Deliverables
Income-specific validated commands/read port using the existing operation protocol;
amount/source/date/note entry, receipt/history, edit/delete/undo and restored drafts.
Stable source kinds are separate from translated labels and optional free text.

### Acceptance criteria
- Dock remains expense-first; an explicit income action opens the actual income form.
  No recurrence or batch options appear. COP/current-or-past date policy is consistent.
- Duplicate/canceled saves recover their original receipts; income updates do not
  touch expenses or budgets. Negative/zero invalid income rejects original input.
- Income deletion and edits use guarded versions/compensation, not a copied unsafe
  implementation; source rename/copy changes cannot change classification identity.
- Use the same approved D07 six-second real-time offer and process-death expiry /
  durable outcome recovery as expense deletion; cover U01–U05 for income too.
- Historical income correction includes contextual/persistent guidance, an explicit
  effective date/affected month and receipt-backed completion feedback (D04/H06).

### Tests
Real-Room income operation contracts and cross-type isolation; ViewModel/Compose
entry-history-edit-delete-undo-restart tests, source-key and exact amount checks; G2.

### Working checkpoint
Expense and income journeys coexist, persist independently and are reachable without
debug data. Extend applicable catalog states and sandbox command scripts.

### Context and starting points
Income powers Resumen but is not a refund/account-transfer classifier. Use the
operation/undo contracts from SELLO-012/019, entry controls and approved source keys
(D05). Read [income](../../design/sello-spec.html#s-ingresos), Anotar and I01.
There is no bank/capture import to reinterpret as income.

### Implementation plan
1. Add income-specific create/edit/delete/read/undo ports and transactional adapters;
   reuse protocol/policies, not a universal expense/investment form with mode booleans.
2. Store positive COP amount/effective date/stable source kind and optional free text/
   note, audit/version/sequence; translated source labels live in app resources.
3. Add explicit income choice from Anotar and history route; dock still opens expense.
   Implement raw draft, busy/recovery/result states and same-operation receipt handling.
4. Add bounded ordered income history, editor and guarded delete/undo using D07;
   expose source values for later snapshot/Resumen rather than calculate budgets here.
5. Add restorable draft/ID UI tests and accessible state fixtures. Existing expenses
   and category budget writes must remain isolated from income commands.

### Concrete cases and pitfalls
- Create income 150,000 twice with one ID: one row. Same ID changed source/amount
  conflicts. Expense totals remain untouched.
- Changing Spanish source copy cannot change its stored classification; custom label
  is metadata, not a new enum inferred from text.
- Income edit/delete uses expected version and durable compensation; it is not safer
  merely because income is positive. Recurrence/batch actions remain absent.

### Verification recipe
Create/run `IncomeCommandContractTest`, `*IncomeFormViewModelTest` and IncomeJourneyTest;
G2. Verify create/list/edit/stale-delete/undo/restart, cross-type isolation and exact
source/date/receipt values through real Room, not a fake summary seed.

### Execution progress
2026-10-12: started on branch `sello-021-income` after SELLO-020 merged. Delivered in
two pull requests, as agreed with the project owner; the ticket stays In Progress
until the second.

**Decisions taken with the project owner before any code**

- Two pull requests: storage and commands first, then the screens.
- Income is started from a "Gasto / Ingreso" choice at the top of the entry form, as
  the design reference shows; the dock still opens an expense.
- There is one offer to undo, shared by expenses and income: a new deletion of either
  kind makes the previous one final.
- The form's preview of the month's income is left out until Resumen exists (SELLO-022).
- Approved by the project owner on 2026-10-13: follow the design's Resumen → Entró
  entry point. The screens pull request adds a minimal working Resumen with shared
  month selection, the real income total and navigation to income history. This is
  an explicit exception to deferring the Resumen destination until SELLO-022;
  no placeholder totals, charts or disabled controls ship. Full Resumen remains
  SELLO-022, and Recibo stays independent of income.

**First pull request: storage and commands, no screens**

- Schema version 3 with a tested migration: `deleted_income`.
- `IncomeCommands` (create, edit, delete, restore, `undoOffer`, `find`) with their
  receipts, and `IncomeReads` (one income, a month's pages, a month's exact total).
- The offer to undo moved out of the expense commands into one `UndoWindow` shared by
  both workflows; the expense contract tests pass unchanged over it.
- Nothing in the app calls the new commands yet, so every existing journey is as it was.
- Verified with the ticket's G2 gate on an API 30 emulator, on the working tree that
  became the commit: run 20261012T235803Z-66aab8c3 passed. Its report is not
  retained; the run that counts as Delivery evidence is the one made when the ticket
  is proposed Done.

**Choices made while implementing, for review**

- Income has its own ordering sequence, separate from the expenses'.
- An edit that changes nothing is refused by the command, and the date is checked only
  when an edit changes it, both as decided for expenses.
- A month's income total is read in the storage adapter with the domain's exact sum;
  no budget policy is involved.

**Second pull request: complete income screens**

- Income entry/receipt, bounded monthly history, guarded edit/delete and the shared
  six-second undo offer now use the actual commands and read ports.
- Gasto / Ingreso preserves each draft; the dock remains expense-first. The approved
  minimal Resumen shows the selected month's real income total and opens history.
- Restored drafts retain generation/version and operation IDs. Unknown outcomes are
  recovered before leaving or sending another write; receipt reads cannot claim an
  unreadable record is an empty success.
- Added catalog income controls, debug-only adaptive/theme previews, screen/journey
  tests and the isolated verification recipe in `docs/testing/SELLO-021-income.md`.
- Full Resumen and the form's month preview remain SELLO-022. The future sandbox
  session/script runner remains SELLO-024/025; its income scenario is documented,
  not implemented as a competing engine. No further SELLO-021 PR is planned.
