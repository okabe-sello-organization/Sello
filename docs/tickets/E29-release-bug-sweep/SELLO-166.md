# SELLO-166 — December 9999 reads its entries

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Expenses and incomes dated December 9999 read in their month instead of an empty one.

### Acceptance criteria
- An expense and an income dated 9999-12-31 show in December 9999's month reads, and
  everything for November 9999 is unchanged.
- The same check on every other month-bounded query.
- Red-first real-Room test. Closes #278.

### Notes
`MonthReads.kt` (`monthExpenses`, `monthIncomes`) bounds the month with
`plusMonths(1).atDay(1)`, which is "+10000-01-01" and sorts before "9999-12-01".
Review: adversarial (storage).
