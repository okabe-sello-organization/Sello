# SELLO-122 — Redesign Pagos e ingresos fijos

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-100, SELLO-004
- **Gate:** G2

### Outcome
Opening Pagos or Ingresos shows the month's total first, a bar of how much of it already
went through, and the schedules grouped by what is still due this month, what went
through, what comes later and what is paused, as in the approved mockup. Every text reads
in both themes.

### Deliverables
- A summary slip: the month's total for the tab, a split bar of recorded against still to
  come, and both amounts under it; incomes in gain ink. No stamp.
- The list grouped as Faltan en {mes}, Ya se anotaron / Ya entraron, Más adelante, En
  pausa and Terminados, each with its count. A row still due shows its date in a pill
  (Hoy / Mañana / the day); a recorded one says when it went through and shows its next
  date; a later one shows its next date.
- `ScheduleRow` gains an optional date pill and a recorded look; the domain line says
  when the month's last occurrence was recorded.
- The title without the month; the empty screen without its Anotar button; the footnote
  saying how to add one.
- `PlanSlip` draws its lines in the theme's ink, so Plan del mes in income history reads
  in the dark theme too.

### Acceptance criteria
- The groups follow the schedule's state on the financial day: ended, paused, something
  still to come this month, everything this month recorded, or nothing this month.
- The total, recorded and still-to-come amounts are the existing exact month plan; a sum
  that does not fit hides the summary, never shows a wrong figure.
- Loading, failed read, empty, large text and both themes keep working; every row stays
  one button that opens its schedule.
- No Anotar button anywhere on the screen; the + stays.

### Tests
- Domain host: the last recorded occurrence in the month, from independent examples.
- App host: the grouping and dates for every state.
- Design-system device: the row's pill and recorded look; `PlanSlip`'s text in the dark theme.
- App device: the screen's groups, the summary and no Anotar button.

### Working checkpoint
On the running app the screen looks like the approved mockup in both themes.

### Context and starting points
`feature/recurrence/SchedulesScreen.kt`, `SchedulesViewModel.kt`, `ScheduleListPolicy`,
`ScheduleRow` and `PlanSlip` in the design system; the mockup in
`artifacts/maqueta-aprobada.html`; the [recurrence examples](../../../testing/recurrence-examples.md).

### Implementation plan
1. The domain's last recorded occurrence in the month.
2. The row and slip parts in the design system, with catalog examples.
3. The grouped state and the screen; the title and the empty state.

### Concrete cases and pitfalls
- Twice a month with one of the two recorded: still due, never "went through".
- A schedule with nothing this month (every few weeks, or starting later) goes to Más adelante.
- An occurrence due today that catch-up has not reached yet is still due, dated today.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-122 --retain`.
