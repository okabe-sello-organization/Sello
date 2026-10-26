# SELLO-124 — Make the ranking part of the income slip

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-123, SELLO-004
- **Gate:** G2

### Outcome
"¿Dónde se va?" and "¿A dónde fue lo que entró?" answered the same question, so the
three spending categories appeared twice on Resumen. They become one slip: what came
in, every spending category as "¿Dónde se va?" ranks it, what was spent and Ingresos
menos gastos, then the stub, as in the mockup in `artifacts/`.

### Deliverables
- The income slip shows, under Entró and its bar, the ranking's basis line and its rows
  (circle, name, share bar against spending, exact amount, opening the category), a
  Gastos line, then Ingresos menos gastos. With no income: the rows and their Total, as
  the ranking was.
- "¿Dónde se va?" no longer has a slip of its own; the income slip's top three and Otras
  lines, and the domain's top-three sum behind them, are removed.
- The catalog's income example and the v2 Resumen paragraphs describe the new slip and
  order.

### Acceptance criteria
- Amounts are exact and come from the existing figures: the rows are the ranking's, the
  Gastos line is the month's spending and the total is the existing Ingresos menos
  gastos. Nothing is projected or estimated.
- No category appears twice on Resumen; every row opens its category.
- Spending above income keeps the total signed and red with both facts; no income shows
  "Aún no anotas ingresos en {mes}.", the rows and their Total, and no negative figure.
- The stub, loading, failed read, stale figures, a past month, large text, both themes
  and the two-pane layout keep working; every target is at least 48dp.

### Tests
- Domain host: the income count from independent examples, kept after the top-three
  sum goes.
- App host: the render model's slip figures (count, bar share, overspent).
- App device: the rows inside the slip, the Gastos and Total lines, no separate
  ranking slip, the order of the slips, and the journeys that open a category, the
  income list and Pagos e ingresos fijos from it.

### Working checkpoint
On the running app the slip looks like the mockup in both themes.

### Context and starting points
`feature/resumen/ResumenScreen.kt` (`IncomeSlip`, `Ranking`), `ResumenRenderModel.kt`,
`ResumenState.kt`; `ResumenPolicy` and `ResumenSnapshot`; `SpendingRankRow` in the
design system; SELLO-123's slip and stub.

### Implementation plan
1. Remove the domain's top-three sum; keep the income count.
2. The slip with the ranking's rows; the ranking slip removed.
3. The catalog example, the device tests and the design reference.

### Concrete cases and pitfalls
- With no income the slip must still rank: Sello works without income.
- With nothing spent there are no rows; the Gastos line says 0 when there is income.
- Bars stay measured against spending, so small categories stay visible.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-124 --retain`.

### Execution progress

- **2026-10-26:** Built as follows, proposed for review with this ticket's pull request.
  The income slip ranks every category under Entró and its bar, with the ranking's basis
  line and rows, then a Gastos line and Ingresos menos gastos; with no income it shows the
  rows and their Total. The separate "¿Dónde se va?" slip, the top three and Otras lines
  and the domain's top-three sum are gone; the income count stays. The catalog example,
  the v2 Resumen paragraphs and HANDOFF follow.

