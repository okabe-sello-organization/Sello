# SELLO-192 — See the month's expenses and incomes day by day

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
One list shows everything recorded in the month, grouped by day, so the person can check
what they spent on a given day and whether something is missing.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/movements-mock.html). The magnifier is
  added to `:design-system` as a bundled icon, like the others.
- Día a día opens from a magnifier on Recibo's and Resumen's bars and from a third
  Resumen stub ("Día a día", "48 en octubre"), always on the shared month. The screen's
  title is Día a día.
- It lists the month's expenses from every category (archived ones included) and its
  incomes, newest day first and newest first within a day. A day's heading names the
  day ("Hoy, mar 6", "Lun 5") and the sum of that day's expenses. A day with only incomes
  shows no sum; incomes are never netted into a day.
- Each row shows the category icon (or the income mark), the note or *Sin nota*, the
  category name or "Ingreso · <fuente>", and the amount. Incomes are green with +. An
  expense recorded by a fixed payment carries *Fijo*.
- The chips Todo / Gastos / Ingresos filter the list. The summary line shows the month,
  Gastaste and Entró, matching Recibo's and Resumen's figures for that month.
- Tapping a row opens today's expense or income editor. A correction or deletion there,
  with its undo, shows up in the list when the person comes back.
- Long months load in pages, as Todos los gastos does. A page that fails keeps what is
  shown and offers a retry.
- Every news state is a `StatusSlip` with its kind (E32). An empty month is `Empty`:
  "Aún no hay movimientos", one line, and the outlined Anotar gasto. Then `LoadFailed`
  with nothing to show, `Older` figures under the notice, and `ActionFailed` for a failed
  page. The chip and month are restored after process death.
- JVM tests with hand-worked literals: 18.000 + 2.950 + 8.000 = 28.950 for a day; a day
  with only a 2.500.000 income has no sum; filters; a day whose sum overflows `Money` is
  a typed failure, never wrapped. Screenshots of every state, light and dark.
