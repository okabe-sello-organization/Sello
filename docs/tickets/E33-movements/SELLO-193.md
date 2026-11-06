# SELLO-193 — Find any expense or income by a word or an amount

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-192
- **Gate:** G1

### Outcome
Typing in Día a día's search finds matching expenses and incomes from every month, such as when
the plumber was last paid and how much.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/movements-mock.html).
- The field searches every month, not only the one on screen. Clearing it returns to the
  month's day list.
- Words match an expense's note and category name, and an income's source name and note.
  Matching ignores accents and capitals as category search does: «rappí» finds «Rappi» and
  «RAPPI». It matches anywhere in the text.
- A query that is a valid COP amount under the D05 input grammar (`18000`, `18.000`)
  also finds expenses and incomes of exactly that amount. A badly grouped number
  (`1.80.00`) is searched only as words.
- Results are grouped by month, newest first. Each group shows its month, its count and
  the sum of its matching expenses. The summary gives the count and sum of all matching
  expenses, and of matching incomes when there are any. The chips filter the results.
- Results load in pages. A reply to an older query never replaces a newer one.
- Nothing found is a `StatusSlip` of kind `Empty` without a button: "No encontramos
  «plomero»" and a hint to try another word or an exact amount. A failed read keeps the
  query and shows `LoadFailed` with its retry. Tapping a result opens its
  editor and comes back to the same results.
- Expenses use the existing folded search key. Incomes may be folded in memory with the
  same rule. A schema change is the owner's decision (epic Decisions).
- JVM tests: folding cases, amount parsing (accepted and refused inputs), month groups
  with literal sums (20.600 + 15.000 + 25.600 = 61.200) and the stale-reply case.
  Screenshots of results, an amount search, nothing found and a failed read.
