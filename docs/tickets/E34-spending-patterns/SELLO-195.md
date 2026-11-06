# SELLO-195 — See which expenses repeat in a category

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
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
  spaces at the ends ignored). Expenses without a note form no group. A group shows the
  most recent spelling, its count and its sum. The top 3 by sum show; a tie goes to the
  higher count, then to the most recent.
- Tapping a group opens Todos los gastos for that category with the note in its search
  field.
- The section follows the detail's loading, failed and older states, as Últimos gastos
  does.
- JVM tests with hand-worked literals: «Rappi, almuerzo» 6 × 20.600 = 123.600; «Mercado
  D1» and «mercado d1 » form one group; a single use is not shown; a tie on sum goes to
  the higher count. Screenshots of the section, light and dark.
