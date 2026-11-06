# SELLO-199 — Fill Anotar with what I record most

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
A fresh Anotar offers the person's most repeated expenses. A tap fills amount, category
and note; nothing is saved until Anotar gasto.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/frequent-mock.html): B and C, with B's
  button where the owner picked. It never adds a row to the form.
- B: the button opens a sheet from the bottom with up to 5 frequent expenses, each with
  its note, category, amount and how many times it was recorded. A tap fills the form
  and closes the sheet.
- C: while writing the note, the composer offers up to 3 past expense notes that start
  with what is typed (folded as search does), most used first, each with its latest
  amount and category. Picking one fills the note, amount and category.
- A frequent expense has the same category, the same note folded as search does, and the
  same amount, recorded 3 or more times in the last 90 days. The sheet lists them most
  repeated first; a tie goes to the most recent. With none, the button is hidden.
- Either way a tap only fills the form. Everything stays editable, and saving is today's single save with its double-submit guard.
- Not shown when correcting an expense, in Anotar varios, for archived categories, or for
  expenses a fixed payment recorded. A failed or slow read hides the button and the
  suggestions; the form never waits for them.
- JVM tests: the 90-day window (day 90 counts, day 91 doesn't), 2 repeats not enough,
  ties, a fill then an edit. Screenshots of the button, the sheet, the composer suggestions and a filled form.
