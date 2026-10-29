# SELLO-100 — Manage recurring payments and incomes

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-098, SELLO-114, SELLO-004
- **Gate:** G2

### Outcome
A person can create, change, pause, resume, end and delete recurring payments and incomes.

### Deliverables
Screens to list and edit schedules, built to the design approved in SELLO-114, including where they are reached from and how a schedule is created.

### Acceptance criteria
- The screens match SELLO-114's approved pages.
- Each schedule kind can be set with the Sello pad for amounts.
- Changes say they apply from now on.
- Deleting keeps recorded occurrences.

### Tests
ViewModel, screen and journey tests; G2.

### Working checkpoint
Schedules can be managed on a phone.

### Context and starting points
SELLO-114's approved pages; the [gap list](../../design/v2/gaps/recurrence.md) names the reusable parts (`SegmentedSwitch`, `LeaderLine`, `ActionLine`, `AmountKeypad`, `ConfirmSlip`, `DangerSlip`, `Stamp`).

### Implementation plan
1. List.
2. Editor.
3. Pause, end, delete.

### Concrete cases and pitfalls
- Keep amounts exact; no rounding.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-100 --retain`.

### Execution progress

- **Built to SELLO-114's locked page** (`docs/design/v2/fijos.html`, owner answers of
  2026-10-25):
  - **Se repite** in Anotar gasto / ingreso: a row under the note and a sheet (each
    month, twice a month, every N weeks or days, an end), shared by both forms through
    `RepeatChoice` and `RepeatDraft`. While it is on the date is today or later and the
    button says **Guardar pago fijo / ingreso fijo**; saving sends `CreateSchedule`, never
    an entry, and recovers an unknown outcome through `ScheduleCommands.find`.
  - **Pagos e ingresos fijos**, from Configuración (with the counts) and Resumen: Pagos /
    Ingresos, the month as a dashed plan (`ScheduleListPolicy`), each schedule by day,
    paused ones stamped, ended ones apart; **+** opens Anotar with the sheet open.
  - **Changing a schedule** in the entry layout: amount, category or source, note, Se
    repite and its start (after today for a new rule), Pausar / Reanudar (saying when a
    category's archiving paused it) and Borrar with a confirm slip; each command is sent
    against the version shown, refusals are said (Catch-up pending, changed, gone).
  - **Solo este / Este y los siguientes** when saving new terms on an entry a schedule
    recorded; the schedule's change is sent first, so a refusal writes nothing, and the
    entry is corrected once it is confirmed.
- **Design system:** `DayGrid`, `IntervalStepper`, `RepeatSheet`, `RepeatSheetActions`,
  `RepeatNext`, `ScheduleRow`, `ChoiceSlip`, `SelloNotice(icon)` and four icons
  (autorenew, pause, play_arrow, event_busy), each in the catalog and `components.html`.
- **Simplified from the page:** the delete card says recorded entries stay without
  counting them, and **+** on the list always opens Anotar gasto (the Gasto / Ingreso
  switch is there). The upcoming-payments list is SELLO-101's; the notice SELLO-103's.
