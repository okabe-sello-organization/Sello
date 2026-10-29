# SELLO-075 — Group thousands in every count the app shows

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
A count of 1,000 or more reads with a thousands separator, as amounts already do:
"10.000 gastos", not "10000 gastos".

### Deliverables
Counts formatted for es-CO wherever the app shows one, with tests at and above 1,000.

### Acceptance criteria
- The restore preview's counts, the restore result ("Se restauró el respaldo: 20
  categorías, 10.000 gastos y 2.000 ingresos"), the erase confirmation, the start-up
  failure restore and the category counts ("Ver los 1.000", "1.000 en 23 días") group
  thousands.
- What a screen reader says matches what is shown.
- Counts under 1,000 are unchanged.

### Tests
Screen or render-model tests with counts of 999, 1,000 and 10,000; G2.

### Working checkpoint
Restoring the SELLO-035 large history shows "10.000 gastos" in the preview and the result.

### Context and starting points
Found in SELLO-035 ([performance profile](../../testing/performance-profile.md)). Counts
reach the strings through `%d` plurals and preformatted `%1$s` arguments such as
`restore_done_body`, `reset_counts_body` and `startup_restore_counts`; amounts already
group thousands through the presentation money formatter.

### Implementation plan
1. List every count the app shows, then format each through one es-CO count formatter
   in `presentation`, keeping plural selection on the number.
2. Add the tests above.

### Concrete cases and pitfalls
- Choose the plural by the number, not by the formatted text.
- Do not change backup files or stored values; only what is shown.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-075 --retain`.

### Execution progress

- **Counts listed, 2026-10-23:** expenses in category detail ("Ver los …", "… en N
  días", "N gastos", "Se muestran N de M"), the archive notice, the restore preview
  table and its spoken cells, the restore result, the erase confirmation, the start-up
  failure restore and the last backup's counts. Days, weeks, months, years and the
  name and note counters stay as they are: none reaches 1,000, and a year must not be
  grouped.
- `presentation/count/countText` groups thousands with a point whatever the phone's
  language; the plurals take it as text (`%s`) and are still chosen by the number.
