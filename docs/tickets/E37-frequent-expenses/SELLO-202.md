# SELLO-202 — Fill Anotar with what I record most

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
A fresh Anotar offers the person's most repeated expenses. A tap fills amount, category
and note; nothing is saved until Anotar gasto.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/frequent-mock.html).
- A frequent expense has the same category, the same note folded as search does, and the
  same amount, recorded 3 or more times in the last 90 days. Up to 3 show, most repeated
  first; a tie goes to the most recent.
- They show under the amount while it is 0 and nothing else was touched. Each chip shows
  the note, the amount and the category icon.
- A tap fills the amount, category and note, and hides the chips. Everything stays
  editable, and saving is today's single save with its double-submit guard.
- Not shown when correcting an expense, in Anotar varios, for archived categories, or for
  expenses a fixed payment recorded. A failed or slow read hides the row; the form never
  waits for it.
- JVM tests: the 90-day window (day 90 counts, day 91 doesn't), 2 repeats not enough,
  ties, a fill then an edit. Screenshots of both states.
