# SELLO-067 — Add the Ajustar presupuesto action to an overspent Recibo

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-062, SELLO-004
- **Gate:** G2

### Outcome
An Excedido month offers **Ajustar presupuesto ›** under the big number, which brings
Recibo's categories card into view, where each category opens its detail and limit
editor.

### Deliverables
The link in the answer slip for an overspent current month, scrolling Recibo to its
categories card and moving accessibility focus to the card's heading.

### Acceptance criteria
- The link appears only with Excedido in the current month, never for a past month.
- Tapping it scrolls Recibo so the **Tus categorías** heading is at the top of the
  list and gives it accessibility focus; nothing navigates away.
- In a two-column layout where the card is already visible, it still moves focus to
  the heading.
- It has a 48dp target and a clear spoken label, such as "Ajustar presupuesto, ir a
  tus categorías".
- Existing limit edits keep their current/past-month rules.

### Tests
Screen test for visibility; scroll-position and focus test; two-column test; G2.

### Working checkpoint
From an Excedido Recibo the link brings the categories card into view, and a
category opens its limit editor as today.

### Context and starting points
**Decision (owner, 2026-10-17): option D**, Recibo's own categories card. It needs no
new screen and works when no single category is over its limit. A dedicated list of
limits was considered and left for later. ADR 0012 names the link; the category
editor is described in [category-editor.md](../../development/category-editor.md).

### Implementation plan
1. Add the link to the SELLO-062 answer slip for Excedido in the current month.
2. Scroll Recibo's list to the categories card and request focus on its heading.

### Concrete cases and pitfalls
- Overspending with no single overspent category is possible (unlimited categories
  count toward the total); the card shows every category, so the link still helps.
- A failed or loading categories card must still be reachable without a crash.

### Verification recipe
Focused screen tests, then `./scripts/verify-ticket SELLO-067 --retain`.
