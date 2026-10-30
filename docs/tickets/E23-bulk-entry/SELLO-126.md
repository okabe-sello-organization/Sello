# SELLO-126 — See the bulk receipts in v2 before they are built

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** None
- **Gate:** G0

### Outcome
A v2 design page shows the bulk receipt for expenses, incomes, a goal and an investment,
in the style of today's entry sheet, and the owner approves it before any screen is built.

### Acceptance criteria
- `docs/design/v2/varios.html` (and its script) draws, in Claro and Oscuro: Anotar varios
  for expenses and for incomes, Aportar varios for one goal, and Varios movimientos for one
  investment, starting from the v1.1 drawings.
- It uses the v2 entry parts: the Sello pad, chips in at most two rows, the note composer,
  **Guardando…**, **No se guardó. Tu borrador sigue aquí.**, **Aún no sabemos si se
  guardó…** with **Comprobar si se guardó**, and a confirmation on the origin screen
  (for example **3 gastos anotados: 126.000**). Nothing offers undo after saving.
- Each receipt shows: opening with one empty line; three lines and the running total; a
  line with an amount but no category, source or type, outlined in loss with the reason;
  removing a line; the 30-line limit; saving; a definite failure; an unknown outcome.
- Expenses and incomes have one date for the receipt, and the switch sets the kind of the
  whole receipt, including what happens to typed lines when it is switched. Goal and
  investment receipts have a date and a type on each line; a USD investment's lines are in
  dollars.
- The entry points are drawn: from single entry, from a category (preselected), from a
  goal's detail and from an investment's detail.
- The [v2 README](../../design/v2/README.md) gets an **Anotar varios** section and a table
  row; the row says it was locked by the owner, with the date. All figures are synthetic.

### Notes
The pull request merges only after the owner approves the page in conversation; the
orchestrator records that approval in the epic's Decisions. Starting points:
`docs/design/sello-spec.html#s-varios`, v2 `entry.html`, `aportar.html`,
`inversiones.html` and `components.html`.
