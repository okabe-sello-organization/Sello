# SELLO-E33 — Every expense and income in one place

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person sees the month's expenses and incomes day by day in one list, and finds any expense or income from any month by a word or an exact amount.
- **Exit:** Movimientos opens from Recibo and Resumen, lists the shared month by day, and its search covers every month, as in the approved mock.

## Decisions

**Owner direction (2026-11-06):** chosen from a list of feature ideas for regular use:
search across everything, and a day-by-day view of the month.

- Only expenses and incomes are listed. Goal and investment movements stay in Metas and
  Inversiones ([ADR 0011](../../decisions/0011-goal-centered-design.md): independent tools).
- No schema change is approved for this epic. If search over incomes needs one (for
  example a stored search key), ask the owner first.

**Planner's calls (2026-11-06), open to the owner:** the screen is called Movimientos; it
opens from a magnifier on Recibo's and Resumen's bars and from a third stub in Resumen;
the search field sits on the same screen; an amount typed in the field finds that exact
amount. The [mock](attachments/movements-mock.html) shows them and needs the owner's
approval before building. Build after E32 so the screen uses `StatusSlip` and the shared
parts.
