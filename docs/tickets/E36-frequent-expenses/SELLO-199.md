# SELLO-199 — Fill Anotar with what I record most

- **Type:** Story
- **Priority:** P2
- **Status:** Ready
- **Depends on:** None
- **Gate:** G1

### Outcome
Anotar offers the person's most repeated expenses. A tap fills the category, the note and
the usual amount; nothing is saved until Anotar gasto.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/frequent-mock.html). It never adds a row
  to the form.
- B: "⚡ Frecuentes" sits at the right end of the Agregar nota row; tapping the rest of
  the row still opens the note. It opens a sheet from the bottom with up to 5 frequent
  expenses, each with its note, category, how many times it was recorded and its usual
  amount ("usualmente 18.000"). A tap fills the form and closes the sheet. With none,
  the button is hidden.
- C: while writing the note, the composer offers up to 3 past expense notes that start
  with what is typed (folded as search does), most used first, each with its category
  and usual amount. Picking one fills the note, the category and the amount.
- A frequent expense is a category and a note folded as search does, recorded 3 or more
  times in the last 90 days, whatever the amounts. The sheet lists them most repeated
  first; a tie goes to the most recent.
- The usual amount is the median of the group's last 10 amounts within those 90 days:
  the middle one with an odd count, or the mean of the two middle ones with an even
  count, rounded half up to the peso. It is computed in checked `Money` (the mean in
  `BigDecimal` with `HALF_UP`), never `Double`.
- Either way a tap only fills the form. Everything stays editable, and saving is today's
  single save with its double-submit guard.
- Not shown when correcting an expense, in Anotar varios, for archived categories, or for
  expenses a fixed payment recorded. A failed or slow read hides the button and the
  suggestions; the form never waits for them.
- JVM tests with hand-worked literals: 17.500, 21.000, 18.000 → 18.000; 17.500 and
  18.000 → 17.750; 18.001 and 18.002 → 18.002 (half up); nine at 18.000 and one at
  45.000 → 18.000; only the last 10 count; the 90-day window (day 90 counts, day 91
  doesn't); 2 uses not enough; ties; a fill then an edit. Screenshots of the button, the
  sheet, the composer suggestions and a filled form.
