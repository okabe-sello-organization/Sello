# SELLO-138 — Delete a category, moving or deleting what it holds

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
From Editar categoría's ⋮ the person deletes a category: straight away when it holds
nothing, otherwise after choosing to move its expenses and fixed payments to another
category or to delete them all.

### Acceptance criteria
- **Placement:** Editar categoría's bar has only ⋮, holding **Archivar categoría** and a
  red **Borrar categoría**. An archived category shows an **Archivada** slip with
  **Desarchivar** in the form, and its ⋮ holds only Borrar categoría.
- **Empty:** a category with no expenses and no fixed payments asks "¿Borrar Café?" with
  Cancelar and a red Borrar; confirming deletes it and its limits.
- **Choose:** Café with 34 expenses for 412.000 (may–oct 2026) and 1 fixed payment says
  so and offers **Moverlos a otra categoría**, **Borrar todo** and a pointer to Archivar.
- **Move:** the list holds every active category except Café, with what each has left
  this month. Choosing Comida (200.000 limit, 80.000 spent in October; Café 60.000 spent,
  50.000 limit) asks "¿Mover a Comida y borrar Café?" with "Gastado en Comida, octubre
  80.000 → 140.000", Café's October limit 50.000 going, and Comida's limit 200.000
  unchanged. Confirming moves the 34 expenses (same dates and amounts) and the fixed
  payment to Comida and deletes Café, its limits and carry. Each month's total is unchanged.
- **Move into a carrying category:** when the moved expenses change Comida's carry
  chain, the existing correction list is shown and must be confirmed.
- **Borrar todo:** the slip counts 34 gastos (412.000), the fixed payment and the
  limits and carry, says past months will show less and that it cannot be undone, and
  **Borrar todo** stays off until BORRAR is typed. Confirming removes all of it.
- **Done:** the editor and the detail close onto Recibo, without Café, with a notice
  (moved: "Café se borró; sus 34 gastos están en Comida"; deleted: "Café y sus 34
  gastos se borraron"; empty: "Café se borró").
- **When it does not happen**, a slip says "No se borró ni se movió nada.", why and the
  way forward: Café changed (new counts, Revisar), the target was archived or deleted
  (Elegir otra), the phone could not save (Reintentar), the counts could not be read
  (no deletion offered, Reintentar), or an unknown outcome is checked with the same
  operation before anything else ("Comprobando…").
- One transaction with an operation ID: a replay returns the first receipt, a double tap
  deletes once, a stale version or changed contents are refused, cancellation is
  rethrown, and a failure leaves every row as it was.

### Notes
[ADR 0024](../../decisions/0024-deleting-a-category.md). The move slip shows exact
facts (spending moved, limits gone or kept) rather than recomputed remaining figures,
which depend on carry and reservations; Recibo shows the result. Fixed payments are
counted, not named. No sandbox scenario: any loaded scenario has categories to delete.
