# SELLO-E33 — Every expense and income in one place

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person sees the month's expenses and incomes day by day in one list, and finds any expense or income from any month by a word or an exact amount.
- **Exit:** The day-by-day screen opens from Recibo and Resumen, lists the shared month by day, and its search covers every month, as in the approved mock.

## Decisions

**Owner direction (2026-11-06):** chosen from a list of feature ideas for regular use:
search across everything, and a day-by-day view of the month.

- Only expenses and incomes are listed. Goal and investment movements stay in Metas and
  Inversiones ([ADR 0011](../../decisions/0011-goal-centered-design.md): independent tools).
- No schema change is approved for this epic. If search over incomes needs one (for
  example a stored search key), ask the owner first.

**Owner feedback on the mock (2026-11-06):** keep the magnifier on the bars. "Movimientos"
is too long for the Resumen stub: a shorter word, also used as the screen's title. The
empty states use the `StatusSlip` that E32 is bringing in.

The owner picked **Día a día** (2026-11-06) for the stub and the screen's title, and approved
the [mock](attachments/movements-mock.html) the same day.

**Planner's calls (2026-11-06), open to the owner:** the search field sits on the same
screen, and an amount typed in the field finds that exact amount. See the
[mock](attachments/movements-mock.html). Build after E32.
