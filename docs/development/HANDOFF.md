# Sello development handoff

> **Informative only.** This file records delivery context and grants no permissions.
> Project permissions and working rules live in [AGENTS.md](../../AGENTS.md),
> subject to the user's current instructions.

**Snapshot:** 2026-10-25 · **Product baseline:** the MVP candidate `0.1.0` ([checklist](../release/mvp-checklist.md)).
Epics E01–E08, E12, E13, E15, E17 and E18 are Done; E16 has work under way. Each ticket's
own `handoff.md` and `evidence.md` under [docs/tickets](../tickets/README.md) carry
the details, and `python3 scripts/quality/tickets.py status` prints the current board.
This summary is refreshed when an epic closes and adds no verification claims.

## Current app

- Offline, local-only COP expenses/income, with Recibo, Resumen and Metas as working tabs.
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
  the same-days comparison, then income, "Un mes normal" over the monthly columns, the
  ranking, the weekday averages (six months) and at most one dismissible suggestion.
- Metas: goals with a target and an optional month, most complete first; the goal page
  with its stamps, Aportes por mes and movements; Aportar/Retirar with past dates,
  corrections and Deshacer. Stamps at 25, 50 and 75 % are celebrated once per confirmed
  save; Marcar como lograda shows the big moment and offers Deshacer for about 10 s
  (30 s with TalkBack), then the goal is read-only. Recibo shows the chosen goal, or the
  most complete one, and a row for another goal at 100 %. Goals never touch budgets or
  income; backups carry them. Rules: [goal examples](../testing/goal-examples.md).
- Pagos e ingresos fijos: Se repite in both entry forms makes a schedule (monthly,
  twice a month, every N days or weeks, optional end). The list, from Configuración and
  Resumen, pauses, resumes, changes from tomorrow and deletes, keeping what was
  recorded; a recorded entry asks Solo este / Este y los siguientes. Sello records due
  occurrences on their own dates when it opens, at the day change and after a save,
  never in the background, and Recibo's bar lists them until Listo. Recibo sets aside
  this month's payments still due: the headline is after them, with Sin margen and
  Revisar, and its footnote opens Pagos previstos. Income history shows Plan del mes.
  Rules: [recurrence examples](../testing/recurrence-examples.md).
- Shared month, restored drafts/navigation, appearance and category-order settings.
- Room schema 5 for finances (goals since 4, recurring schedules since 5); DataStore
  for nonfinancial preferences.
- Backups save and restore through the system file picker, also from the start-up
  failure screen; retry and confirmed start-over preserve an unreadable database privately.
  Restaurar also imports a FinanceTracker backup, investments included, showing what it
  changed and left out first (ADR 0019).
- Configuración → Borrar todos los datos erases the history after counts and BORRAR.
- No network permission or OS backup; every build checks its manifests, release
  libraries and APK against [MVP data handling](../privacy/mvp-data-handling.md).

The modules are `:app`, `:domain`, `:data`, `:design-system` and the separate
`:catalog`. The extra `:prototype` app renders v1.1 sample screens with no storage.
[Sello v2](../design/v2/README.md) is the approved design. Category detail retains
the v1.1 presentation with the owner's section order and the calculations in
[ADR 0015](../decisions/0015-category-detail-insights.md). Recibo's status is factual
under ADR 0012 BG1 (SELLO-061); [ADR 0012](../decisions/0012-budget-guidance-contract.md)
tracks the remaining financial inputs.

## Ticket handoffs

What each ticket changed is in `docs/tickets/<epic>/<ticket>/handoff.md`, next to its
evidence and retained report. This file no longer repeats them.

## Remaining work

- Distributing the `0.1.0` APK is a separate, owner-authorized action; nothing was
  published or tagged.
- SELLO-074 ([E14](../tickets/E14-assistive-technology/README.md)): the manual
  TalkBack, Switch Access and keyboard pass, moved post-MVP by the owner on
  2026-10-23. E13 is done without it.
- SELLO-079, SELLO-083, SELLO-084, SELLO-105–113 ([E16](../tickets/E16-v2-today-gaps/README.md), [E19](../tickets/E19-investments/README.md)):
  the rest of today's v2 screens (SELLO-080–082 and SELLO-117 are done; the SELLO-079
  audit credits them), then Inversiones, released with Metas and recurrence
  ([E18](../tickets/E18-recurrence/README.md), done) as `0.2.0`; decisions in
  [ADR 0017](../decisions/0017-goals-recurrence-investments.md). Each epic starts by
  verifying what already exists.
- Signed carryover (E09) and the assistive-technology pass (E14) stay on the post-MVP
  roadmap. Legacy import (E10) is done: Restaurar also takes a FinanceTracker backup,
  investments included ([ADR 0019](../decisions/0019-legacy-import.md)).

Every MVP ticket is Done. The post-MVP epics E09, E10 and E14–E19 live under
[docs/tickets](../tickets/README.md).

## Detail references

- [Current app inventory](current-app-experience.md) and [navigation graph](navigation-graph.md).
- [Architecture](../../ARCHITECTURE.md), [current decisions](../decisions/0001-mvp-contract.md) and [design guide](../design/README.md).
- [Storage](storage.md), [backup format](../data/backup-format.md), [command recovery](commands.md) and [monthly snapshots](monthly-snapshot.md).
- [Setup](setup.md), [device-test notes](execution-guide.md#device-test-notes) and [quality flow](quality-gates.md).
- [Post-MVP roadmap](../tickets/README.md).
