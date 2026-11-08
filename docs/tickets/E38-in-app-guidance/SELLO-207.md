# SELLO-207 — Recibo and categories explain themselves

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-204, SELLO-206
- **Gate:** G2

### Outcome
On Recibo and the category screens, a **?** after a card's title explains the card and an
underlined term explains one word, in plain Spanish that matches what the app computes.

### Acceptance criteria
- **? on these cards**, each opening a help sheet with a link to the right Guía page:
  - Recibo summary card ("Restante del presupuesto"): how Restante, por día, Límite del
    mes and Gastado relate, as in the [mock](attachments/guidance-mock.html).
  - Recibo "Separado en…" (pagos previstos): why that money is already set aside.
  - Category detail, Pagos fijos: what a fixed payment does to the budget.
  - Category detail, the monthly history: base limit plus what carried from earlier
    months.
- **Underlined terms**, each opening a sheet for that word: **por día** (Recibo),
  **Al día** (category detail's per-day figure), **base** and **sobrante / faltante**
  (Recibo summary and category history), **arrastre** (category editor switch),
  **Separado** (Recibo, category detail).
- Each explanation states the rule the code applies today, with one small worked
  example in round numbers that are not anyone's data. Wording differs where the rule
  differs (for example por día counts today; say so).
- Nothing else on these screens moves: no new rows, no changed figures, no new
  buttons beyond the **?**. Loading, empty, error and stale states keep their **?** only
  where the card itself shows.
- Screenshot tests show the summary card with its **?** and underlines and one sheet, in
  light and dark; a ViewModel or UI test opens each sheet.

### Notes
- Lighter review. The owner reviews the copy; cite in the pull request the code each
  explanation was taken from.
- "Al día" also names Recibo's on-track stamp: that double meaning is a separate copy
  issue, not part of this ticket.
