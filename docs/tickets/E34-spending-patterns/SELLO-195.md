# SELLO-195 — See which expenses repeat in a category

- **Type:** Story
- **Priority:** P2
- **Status:** Ready
- **Depends on:** None
- **Gate:** G1

### Outcome
Category detail shows the notes the person repeats most in the month, so "Comida
900.000" becomes "Mercado D1, 4 veces" and "Rappi, 6 veces".

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- "Lo que más se repite" follows Últimos gastos when a note is used 2 or more times in
  that category in the selected month. Otherwise the section is absent.
- Expenses are grouped by their note folded as search folds it (accents, capitals and
  spaces at the ends ignored). An expense without a note, or with only spaces, is never
  grouped. A group shows the most recent spelling, its count and its total.
- The section reads as a small table headed Nota · Veces · Total. A line under it says
  the total is the sum of every expense with that note in the month, and that expenses
  without a note don't count. The top 3 by sum show; a tie goes to the
  higher count, then to the most recent.
- Tapping a group opens Todos los gastos for that category with the note in its search
  field.
- The section follows the detail's loading, failed and older states, as Últimos gastos
  does.
- JVM tests with hand-worked literals: «Rappi, almuerzo» 6 × 20.600 = 123.600; «Mercado
  D1» and «mercado d1 » form one group; a single use is not shown; expenses without a note or with only spaces form no group; a tie on sum goes to
  the higher count. Screenshots of the section, light and dark.
