# SELLO-197 — See the year's records

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-196
- **Gate:** G1

### Outcome
Tu año ends with the year's records: the dearest and the calmest month, the biggest
expense, and the category that went over its limit most often.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- Records use only finished months:
  - **Mes más caro:** the month with the most spending.
  - **Mes más tranquilo:** the month with the least spending among those with at least
    one expense.
  - **Gasto más grande:** the largest single expense of the year, with its note and date.
  - **Más veces sobre su límite:** the category with the most Excedido months, shown
    from 2 months up.
  - Ties go to the earlier month, the earlier expense, or name order for categories.
- With fewer than 2 finished months the slip says "Con un mes más verás tus récords."
- JVM tests with hand-worked literals: March 1.840.000 dearest, August 1.020.000 calmest,
  a 1.200.000 expense on 3 February the biggest; ties; a year with one finished month.
  Screenshots of the slip and its short-history state.
