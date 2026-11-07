# SELLO-194 — See how much small purchases add up

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
Resumen tells the person how much their gastos hormiga came to this month, compared with
the month before, and where they gather.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- A Gastos hormiga slip follows Resumen's income slip when the month has 5 or more
  expenses at or under the line. Otherwise there is no slip and no empty card.
- It counts and adds the expenses whose amount is at most the line, in every category,
  fixed payments included, over the same days as Resumen's hero: up to today in the month
  in progress, the whole month once it has finished.
- It shows the share of that period's spending, rounded half up to a whole percent:
  312.000 of 2.480.000 is 12,58 %, shown as 13 %. It compares with the same days of the
  previous month only when that month has data (Resumen's rule). It lists the 3
  categories with the biggest sums; a tie goes to the higher count, then to name order.
- Setting the line follows the Tasa del dólar flow. Configuración gets a Resumen section
  with one `SettingRow`: Gastos hormiga, "Los que suma como pequeños", and the value
  ("Hasta 20.000"). Tapping it opens the same full-screen form as Tasa del dólar,
  without the date: the amount on the Sello pad (whole pesos, so 000 instead of a
  comma), today's value under it, and Guardar. Values
  run from 1.000 to 100.000, default 20.000. Any other value is refused with a message
  and nothing is saved. A saved value applies to every month, past ones too.
- The line is a portable preference. It travels in backup format version 8; older
  files read as 20.000.
  Restore and Borrar todos los datos reset it like the other preferences. The backup
  format page is updated.
- The slip follows Resumen's loading, failed and older states.
- JVM tests with hand-worked literals: with a line of 20.000, the expenses 20.000,
  20.001, 8.000 and 2.950 give 3 small ones adding to 30.950; rounding 0,5 % → 1 % and
  0,49 % → 0 %; 4 small expenses give no slip; the comparison is absent without data.
  Screenshots of the slip, the Configuración row and the form with its refused and
  saving states.
