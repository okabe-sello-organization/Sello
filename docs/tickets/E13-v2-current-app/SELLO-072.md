# SELLO-072 — Bring Resumen to v2 for today's figures

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-064, SELLO-065, SELLO-004
- **Gate:** G2

### Outcome
Resumen shows today's figures in the v2 order and components: income lines first,
then month by month, then where the money goes.

### Deliverables
Resumen rebuilt with v2 components for the figures it already computes: the income
slip as receipt lines with **Ver ingresos y su destino ›**, the monthly columns with
the month in progress striped, and categories ranked by spending with
`SpendingRankRow`, plus updated screen tests.

### Acceptance criteria
- Only figures the app already computes appear; nothing is projected for the month
  in progress.
- Columns give exact values on selection, as today; the striped current month is
  explained to screen readers.
- Each ranked category opens its detail; the total line matches the sum.
- Loading, failed (never zero), empty and stale states keep today's behaviour.
- Large text, 320dp and two-column layouts work.

### Tests
Resumen screen and journey tests updated; chart exact-selection tests; G2.

### Working checkpoint
On a phone, Resumen shows the v2 income lines, columns and ranking for the selected
month, and each opens what it opens today.

### Context and starting points
Today: "4. Resumen" in the current app inventory and `app/.../feature/resumen`.
Reference: `docs/design/v2/resumen.html` and "Resumen" in the v2 README. The v2
hero ("¿Cómo va este mes?" against the same days of last month), "¿Qué días gastas
más?" and the suggestion need new calculations and are not part of this ticket; the
owner can ticket them separately.

### Implementation plan
1. Reorder Resumen's slips to the v2 order.
2. Replace the income and allocation slips with receipt lines.
3. Replace category columns with ranked rows.
4. Update tests.

### Concrete cases and pitfalls
- Removing the cumulative daily line chart changes what Resumen shows; keep it until
  the owner confirms it goes, and record the decision here.
- The month in progress must never be drawn as a full month.

### Verification recipe
`:app` focused tests, then `./scripts/verify-ticket SELLO-072 --retain`.
