# SELLO-196 — See the year at a glance

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
Tu año shows what the person spent in a year, how it compares with the year before, how
each month closed, and where the money went.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- Resumen's Mes a mes gets "Ver tu año ›", which opens Tu año on the year of the month
  on screen. ‹ and › move between the first year with records and the current year.
- The hero shows the year's expenses. A year in progress says "del 1 de enero al <hoy>"
  and never projects. It compares with the same days of the year before only when that
  year has data for them. 29 February is capped at 28 February in a shorter year, as
  Resumen caps month ends. The percentage uses Resumen's rounding.
- Mes a mes: 12 columns. A finished month carries its verdict, Cumplido ✓ or Excedido
  ✗, from its own limit and carry
  ([ADR 0012](../../decisions/0012-budget-guidance-contract.md) BG1). A month without a
  limit has no mark. The month in progress is in the brand ink; later months are empty
  outlines. Then a count line ("8 meses cumplidos · 2 excedidos"). Selecting a column
  shows its exact value.
- Lo que entró: the year's income, expenses and Ingresos menos gastos, signed.
- ¿En qué se fue?: categories by their year total with their share (half up, whole
  percent), archived ones included. The top 5 show, then "Ver las N categorías".
- States: loading, a year with nothing recorded, a failed read, and older figures under
  the notice. The year shown is restored after process death.
- JVM tests with hand-worked literals: 14.230.000 against 15.350.000 is 1.120.000 less,
  ↓ 7,3 %; verdicts for a month under, equal to, over and without its limit; shares that
  round to 46 %, 27 %, 10 %, 8 % and 4 %. Screenshots of each state, light and dark.
