# SELLO-018 — Deliver Recibo, category detail and expense history

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-014, SELLO-017, SELLO-009, SELLO-004
- **Gate:** G2

### Outcome
The first slice answers what remains and lets users inspect the source expenses.

### Deliverables
Real Recibo/category-circle grid, current/past month slips, category detail and
bounded expense list using shared snapshots; loading/empty/error/last-good states,
category-scoped Anotar, sort defaults, and adaptive list/detail selection.

### Acceptance criteria
- Saved expense updates total/category at a consistent revision; detail/list amounts
  reconcile to the receipt. No home pace graph, warning card or duplicate rule engine.
- Prior month hides daily allowance and uses its saved budget; unlimited categories
  sort last. All-time/list pagination does not silently truncate totals or date range.
- First/subsequent read failure is visible and retryable, never an empty success.
- Large values/font scales do not clip; category status is not color alone.

### Tests
Snapshot/render-model tests, device save→detail→month→restart journey, injected read
failures, empty versus failed state, expanded layout and exact accessibility amounts.

### Working checkpoint
The complete walking slice works on device and all prior tickets' gates remain green.
Sandbox scenarios can now exercise meaningful real financial state.

### Context and starting points
Users must trace the receipt back to consistent persisted totals and history.
Use SELLO-014 snapshot/failed states and SELLO-017 entry result. Read
[Recibo](../../design/sello-spec.html#s-recibo), category detail and architecture §6;
MVP detail is budget/actuals/history, not all deferred comparison/record charts.

### Implementation plan
1. Map one snapshot to Recibo totals/verdict/allowance/category cells, keeping exact
   values and original revision/as-of labels. UI formatting cannot calculate finance.
2. Add category detail with spent/limit/remaining and bounded ordered expenses, explicit
   paging/all-record access and category-scoped Anotar prefill.
3. Implement current/past/no-limit/loading/empty/error/last-good branches, selected
   month/detail restoration and adaptive list/detail behavior.
4. Apply approved default ordering (D06), unlimited-last and accessible labels; keep
   unsupported weekly/comparison/export links absent, not successful placeholders.
5. Reconcile new receipt/edit events through repository observation, not imperative
   local subtraction of displayed text. Record example states in previews/catalog as applicable.

### Concrete cases and pitfalls
- B01/H01 produce exact totals across home/detail/list after month change/restart.
- Read fault with previously loaded records shows failed/stale state, never “no expenses”.
  Empty fresh install remains a genuine different state.
- Pagination may limit visible rows, not aggregate totals; no arbitrary 2000–2099
  “all-time” filter or Float source values.

### Verification recipe
Create/run `*ReciboRenderModelTest`, `*CategoryDetailViewModelTest` and the device
WalkingSliceJourneyTest; G2. Save then browse detail/month/history/restart; record
receipt-to-list-to-total reconciliation and a deliberately injected read-failure state.

### Execution progress
2026-10-10: started on branch `sello-018-recibo-detail-history` after SELLO-017
merged. No dependency was added and the schema did not change.

Delivered. `:domain`: `CategoryMonth.expenseCount`, `CategoryOrderPolicy` and a paged
`ExpenseReads.inCategory`. `:data`: its query and `RoomExpenseReads`. `:app`: the real
Recibo, the category detail with its expense list, `ReciboRenderModel` shared by
both, the open category kept in the frame's saved state, and the list/detail layout.
`:design-system`: the scaffold keeps content clear of a system bar at the side.
`docs/development/recibo-and-detail.md` states the rules and contracts.

Choices made during the work, for owner review:
- A finished month over its limit is stamped "Sobre el límite", as the stamps table
  says. The Recibo text of the reference says "Pasado" for the same case.
- The detail is the header, spent, limit and the list. The per-category daily amount,
  the comparison with last month, the weekly and six-month charts and "En números"
  are not there: the snapshot has no such figures and the ticket leaves them out.
- The list shows 20 expenses at a time with "Ver más" and "Se muestran 20 de 45",
  on the same screen. The reference shows four and opens a separate screen with
  sort and search.
- An expense without a note is listed as "Sin nota" with its day underneath.
- "Anotar aquí" is offered only in the month in progress and for a category that is
  not archived, because a new expense is dated today.
- Side by side needs a two-column window at least 600dp wide. A short, narrow
  window (half a phone's screen) shows one screen at a time, although the layout
  rules class it as two-column. The entry form still splits in such a window.
- Beside Recibo, the first category is shown until one is chosen, and Back does
  what it does on Recibo.
- Long-press on a category, swiping between months, the gear, and the count-up and
  stamp motion are absent.

Found on the way: on a phone on its side the second column and the dock ran under
the system's buttons and cut amounts off. `SelloScaffold` applied side insets to
the top bar only; it now applies them to the panes and the dock, with a test that
failed first. Two older journeys asserted the previous Recibo's text and now assert
the new sentences. The month selection lasts as long as the process, so device
tests that change it put it back.

Left for later: undo, edit and delete (SELLO-019, SELLO-020); choosing the order
(SELLO-023); sandbox scenarios (SELLO-024); a screen for a database that cannot be
opened at start, which still has no ticket.
