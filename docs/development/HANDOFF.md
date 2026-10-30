# Sello development handoff

> **Informative only.** This file records delivery context and grants no permissions.
> Project permissions and working rules live in [AGENTS.md](../../AGENTS.md),
> subject to the user's current instructions.

**Snapshot:** 2026-10-29 · **Product baseline:** the MVP candidate `0.1.0` ([checklist](../release/mvp-checklist.md)).
Every epic is Done: E01–E10, E12, E13 and E15–E22. The owner dropped E14 and the
release tickets SELLO-043 and SELLO-113 on 2026-10-29. Each ticket's
file under [docs/tickets](../tickets/README.md) and the pull request that closed it carry
the details, and `python3 scripts/quality/tickets.py status` prints the current board.
This summary is refreshed when an epic closes and adds no verification claims.

## Current app

- Offline, local-only COP expenses/income, with Recibo, Resumen, Metas and Inversiones
  as working tabs.
- Category creation/editing, archive/unarchive, monthly limits and past corrections.
  The editor opens with the keyboard on the name, offers 24 icons and the person's
  most used limits, and says what the month's total limit would become.
- Category detail follows the owner's hero → latest expenses → En números → weekly
  spending → historical average order, with real insights and a searchable expense
  browser. Its current-month chart point shares the hero's live limit; the average
  still uses completed months. See [the detail guide](recibo-and-detail.md#category-detail).
- Main and tools launchers now use the existing Marca artwork, including themed icons.
- Expense/income history, editing, deletion and guarded six-second undo.
- Exact totals, budget remaining/allowance, net flow and allocation. Resumen leads with
  the same-days comparison, then where the income went
  ([E22](../tickets/E22-income-receipt/README.md)): what came in, every category ranked
  by what it took (the former "¿Dónde se va?"), Ingresos menos gastos, and a stub to the
  income list and Pagos e ingresos fijos. Then "Un mes normal" over the monthly columns,
  the weekday averages (six months) and at most one dismissible suggestion.
- Metas: goals with a target and an optional month, most complete first; the goal page
  with its stamps, Aportes por mes and movements; Aportar/Retirar with past dates,
  corrections and Deshacer. Stamps at 25, 50 and 75 % are celebrated once per confirmed
  save; Marcar como lograda shows the big moment and offers Deshacer for about 10 s
  (30 s with TalkBack), then the goal is read-only. Recibo shows the chosen goal, or the
  most complete one, and a row for another goal at 100 %. Goals never touch budgets or
  income; backups carry them. Rules: [goal examples](../testing/goal-examples.md).
- Pagos e ingresos fijos: Se repite in both entry forms makes a schedule (monthly,
  twice a month, every N days or weeks, optional end). The list, from Configuración and
  Resumen ([E21](../tickets/E21-fixed-payments-screen/README.md)), leads with the month's
  total and a bar of what went through, ending in a **+ Nuevo pago fijo** stub, then groups Faltan, Ya se anotaron, Más
  adelante, En pausa and Terminados; it pauses, resumes, changes from tomorrow and
  deletes, keeping what was recorded; a recorded entry asks Solo este / Este y los siguientes. Sello records due
  occurrences on their own dates when it opens, at the day change and after a save,
  never in the background, and Recibo's bar lists them until Listo. Recibo sets aside
  this month's payments still due: the headline is after them, with Sin margen and
  Revisar, and its footnote opens Pagos previstos. Income history shows Plan del mes.
  Rules: [recurrence examples](../testing/recurrence-examples.md).
- Inversiones: COP or USD investments with aportes, retiros, dividends and recorded
  values, the portfolio total in pesos at one dated, manually entered dollar rate, and
  each investment's simple return. Rules: [investment examples](../testing/investment-examples.md).
- Configuración ([E20](../tickets/E20-settings/README.md)) opens on Tus datos: a backup
  card with the last backup's date, age and a Guardado, Pendiente (after 30 days) or
  Sin respaldo stamp, Respaldar and Restaurar right there, Exportar a CSV o JSON (saved
  through the picker or shared through a one-file FileProvider) and Borrar todos los
  datos. Then Lo que se repite, Apariencia (theme, ink swatches), Recibo (category order
  and direction) and Inversiones (starting currency, list order, Dólar rate).
- Signed carryover ([E09](../tickets/E09-signed-carryover/README.md)): per category,
  opt-in and off by default; next carry = (base + carry) − spending, with no cap.
  Verdicts and allowance use base + carry; turning off or archiving with a balance
  confirms dropping it; past corrections preview and replay later months. Rules:
  [ADR 0020](../decisions/0020-signed-carryover-policy.md).
- Shared month and restored drafts/navigation.
- Room schema 7 for finances (goals since 4, recurring schedules since 5, investments
  since 6, carryover since 7); DataStore for nonfinancial preferences; backup format version 6
  ([format](../data/backup-format.md)).
- Backups save and restore through the system file picker, also from the start-up
  failure screen; retry and confirmed start-over preserve an unreadable database privately.
  Restaurar also imports a FinanceTracker backup, investments included, showing what it
  changed and left out first (ADR 0019).
- Configuración → Borrar todos los datos erases the history after counts and BORRAR.
- No network permission or OS backup; every build checks its manifests, release
  libraries and APK against [MVP data handling](../privacy/mvp-data-handling.md). The
  only provider that grants access is the export one, limited to `cache/exports/`.

The modules are `:app`, `:domain`, `:data` and `:design-system`.
[Sello v2](../design/v2/README.md) is the approved design. Category detail retains
the v1.1 presentation with the owner's section order and the calculations in
[ADR 0015](../decisions/0015-category-detail-insights.md). Recibo's status is factual
under ADR 0012 BG1 (SELLO-061); [ADR 0012](../decisions/0012-budget-guidance-contract.md)
tracks the remaining financial inputs.

## Ticket handoffs

What each ticket changed is in its pull request, linked from the ticket file
`docs/tickets/<epic>/SELLO-0NN.md`. This file does not repeat them.

## Remaining work

- Distributing the `0.1.0` APK is a separate, owner-authorized action; nothing was
  published or tagged.
- No ticket is open. Dropped on 2026-10-29: SELLO-043 (carryover release), SELLO-113
  (the `0.2.0` release) and E14 with SELLO-074 (manual TalkBack, Switch Access and
  keyboard walks). The automated accessibility checks from SELLO-033 stay.
- Open GitHub issues are not tickets.

All epics live under [docs/tickets](../tickets/README.md).

## Detail references

- [Architecture](../../ARCHITECTURE.md), [current decisions](../decisions/0001-mvp-contract.md) and [design guide](../design/README.md).
- [Storage](storage.md), [backup format](../data/backup-format.md), [command recovery](commands.md) and [monthly snapshots](monthly-snapshot.md).
- [Setup](setup.md), [device-test notes](execution-guide.md#device-test-notes) and [quality flow](quality-gates.md).
- [Post-MVP roadmap](../tickets/README.md).
