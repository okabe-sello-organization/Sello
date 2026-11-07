# Sello development handoff

> **Informative only.** This file records delivery context and grants no permissions.
> Project permissions and working rules live in [AGENTS.md](../../AGENTS.md),
> subject to the user's current instructions.

**Snapshot:** 2026-11-07 · **Product baseline:** `main`; the release candidate is `0.1.0` from `a20ae90f`, and the [candidate checklist](../release/candidate-checklist.md) leaves only the owner's steps (the [MVP checklist](../release/mvp-checklist.md) is the October record).
Done: E01–E10, E12, E13, E15–E32 and E35–E37; E33 and E34 are open. The owner dropped E14 and the
release tickets SELLO-043 and SELLO-113 on 2026-10-29. Each ticket's
file under [docs/tickets](../tickets/README.md) and the pull request that closed it carry
the details, and `python3 scripts/quality/tickets.py status` prints the current board.
This summary is refreshed when an epic closes and adds no verification claims.

## Current app

- Offline, local-only expenses/income in COP (investments also in USD), with Recibo,
  Resumen, Metas and Inversiones as working tabs.
- Category creation/editing, archive/unarchive, monthly limits and past corrections.
  Editar categoría's ⋮ holds Archivar and Borrar categoría
  ([E26](../tickets/E26-delete-category/README.md), ADR 0024): a category is deleted after
  one confirm slip when empty, otherwise after moving its expenses and fixed payments to another
  category or deleting them all behind a typed BORRAR; no undo.
  The editor opens with the keyboard on the name, offers 24 icons and the person's
  most used limits, and says what the month's total limit would become.
- Category detail follows the owner's hero → latest expenses → En números → weekly
  spending → historical average order, with real insights and a searchable expense
  browser. Its current-month chart point shares the hero's live limit; the average
  still uses completed months. See [the detail guide](recibo-and-detail.md#category-detail).
- Main and tools launchers now use the existing Marca artwork, including themed icons.
- Expense/income history, editing, deletion and guarded six-second undo.
- Anotar varios ([E23](../tickets/E23-bulk-entry/README.md),
  [E25](../tickets/E25-varios-everywhere/README.md),
  [E27](../tickets/E27-varios-across/README.md)): several expenses or several incomes on
  one receipt, a goal receipt across several goals and an investment receipt across several
  investments, each line picking its own; mixed COP/USD totals in COP at today's rate.
  Anotar varios also sits at the foot of Metas and Inversiones. Each receipt holds one kind,
  set by where it opens. Every entry sheet's Varios pill hands
  what is typed on as line one. Saved whole or not at all, up to 30 lines, no undo after
  saving.
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
  (30 s with TalkBack), then the goal is read-only. Up to five goals can be starred
  ([E28](../tickets/E28-recibo-goal-carousel/README.md)); a sixth star picks the one it
  replaces. Recibo's goal pill is a looping carousel of up to five goals, starred first,
  then the most complete, with dots under it, and a row for another goal at 100 %. Goals never touch budgets or
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
- Gastos frecuentes ([E36](../tickets/E36-frequent-expenses/README.md)): Anotar's note
  line ends in ⚡ Frecuentes, a sheet of up to 5 category-and-note pairs recorded 3+
  times in 90 days (notes folded as search folds them), each with its usual amount, the
  median of its last 10. The note composer suggests up to 3 past notes that start with
  what is typed. A tap only fills the form; Anotar gasto saves it. Not when correcting,
  nor for fixed payments or archived categories.
- Tinta propia y del sistema ([E37](../tickets/E37-tinta/README.md)): Apariencia › Tinta
  offers Cobalto, Violeta, Sistema and Tuya. One generator in `:design-system` turns
  Cobalto to any hue, keeping every contrast Cobalto's. Tuya is a hue the person picks in
  the Tu tinta sheet; Sistema takes the phone's accent hue (Android 12 and later, not
  offered on Android 11, where a restored Sistema shows Cobalto) in Sello's own shades,
  read again each time Sello is on screen. Red and green hues get a note; gain, loss and
  warn never follow the ink. Backups carry the ink and Tuya's hue.
- Compartir el mes ([E35](../tickets/E35-share-the-month/README.md)): Resumen's bar
  shares the month on screen as an image, after a preview sheet that shows it and says
  what it holds: the month, what is left of the total limit with its stamp, spent
  against that limit and each category Recibo lists with its spent and limit. Never
  notes, income, goals, investments, settings or payments still to come. Light paper in the person's ink; older figures wait for a fresh read. The PNG
  goes through the export FileProvider in `cache/exports/`. Resumen's bar also gained
  Configuración.
- Signed carryover ([E09](../tickets/E09-signed-carryover/README.md)): per category,
  opt-in and off by default; next carry = (base + carry) − spending, with no cap.
  Verdicts and allowance use base + carry; turning off or archiving with a balance
  confirms dropping it; past corrections preview and replay later months. Rules:
  [ADR 0020](../decisions/0020-signed-carryover-policy.md).
- Shared month and restored drafts/navigation.
- Room schema 9 for finances (goals since 4, recurring schedules since 5, investments
  since 6, carryover since 7, up to five starred goals since 8, indexes for long
  histories and an accent-blind expense search key since 9); DataStore for
  nonfinancial preferences; backup format version 9 (8 added Tuya's hue, 9 the ink
  Sistema), unchanged by schema 9, which only adds derived data
  ([format](../data/backup-format.md)).
- Backups save and restore through the system file picker, also from the start-up
  failure screen; retry and confirmed start-over preserve an unreadable database privately.
  Restaurar also imports a FinanceTracker backup, investments included, showing what it
  changed and left out first (ADR 0019).
- Configuración → Borrar todos los datos erases the history after counts and BORRAR.
- Release bug sweep ([E29](../tickets/E29-release-bug-sweep/README.md)): every finding of
  the 2026-11-03 code audit and six open bug issues fixed. For the person: a failed read
  keeps the last good figures on every tab and form instead of an empty or zero screen;
  a save whose outcome is unknown stays open in the amber warning until it is checked,
  and is never sent twice; catch-up saves whole batches in the right zone and its notice
  lists everything it recorded, even after the clock moves back
  ([ADR 0025](../decisions/0025-catch-up-stamps-after-the-notice-mark.md)); Pagos fijos
  counts what was really recorded, and a schedule change never records the past; reset
  and restore finish off the main thread and never undo each other; a damaged backup or
  FinanceTracker file is refused; names with invisible characters can't pass for
  another; expense search ignores accents and case. The walk of the R8 release build
  through every journey since the MVP is in the
  [performance profile](../testing/performance-profile.md#release-walk-since-the-mvp-sello-169).
- Release candidate ([E30](../tickets/E30-release-candidate/README.md)): the 2026-11-04
  audit's before-release findings fixed. Reintentar reads again on every form, and Anotar
  varios and the schedule editor keep working on the last good figures; the status-bar
  icons follow what v2 draws under them in Claro and Oscuro, and a dark phone starts on
  the dark desk; restore and erase name every setting they reset, a FinanceTracker file
  on the start-up failure screen is named as one, and a previewed backup's plain copy is
  deleted at the next start after a day; name order folds accents and Ñ as search does,
  Solo este is asked once, and every chart writes months alike. The candidate's records
  were refreshed (SELLO-174), and two device tests fixed (#371 the flaky reset journey,
  #372 the newer-schema tests). The release check passed up to the owner's key, in
  parts: the gate run up to `release-production`, which failed on a missing `java` on
  PATH; that step alone, refusing only the throwaway certificate; and the launch alone
  on the same new API 30 emulator. Then the advisory check and a walk on a new API 37
  emulator ([walk](../testing/performance-profile.md#release-candidate-walk-on-api-37-sello-175)).
- Design system from the built app ([E31](../tickets/E31-design-system/README.md)),
  documentation only: the pages under [docs/design/system](../design/system/index.html)
  are the only design reference ([ADR 0026](../decisions/0026-design-system-from-the-built-app.md));
  v2, the v1.1 snapshot and the screen briefs are gone. Each feature page describes every
  screen and state with real JVM screenshots, light and dark, at the S25 Ultra's
  412x891 dp. The [findings page](../design/system/findings.html) lists misuse, copies and
  hard-coded values, colours first; its Dynamic colours box says dynamic colours need
  `SelloColors` built from the system scheme, not only a new Material scheme. The bugs
  the pages found are issues #374–#387 and #394.
- The code follows its design system ([E32](../tickets/E32-design-system-refactor/README.md)):
  every finding of E31 fixed, so the [findings page](../design/system/findings.html) lists
  none. One `StatusSlip` whose kind (Empty, LoadFailed, Older, ActionFailed, Unknown, Gone)
  decides its look replaces the three old slips; every dialog is a `DialogSlip` whose
  answers the slip draws, with one button order (stacked, the action on top). Parts two
  features share moved to `:design-system` (the sheet is `SelloSheet`); screens read the
  tokens for type, spacing, shape and alpha, and every colour comes from the theme (a
  `scrim` role; unused category colours and `dividend` deleted). For the person: turning
  carry on or off in Editar categoría waits for Guardar, Retirar has its own icon, and
  the same thing looks alike everywhere. Once SELLO-191's pull request merges, its merge
  commit on `main` is tagged `design-system-v1`, a marker of this design, not a release.
- No network permission or OS backup; every build checks its manifests, release
  libraries and APK against [MVP data handling](../privacy/mvp-data-handling.md). The
  only provider that grants access is the export one, limited to `cache/exports/`.

The modules are `:app`, `:domain`, `:data` and `:design-system`.
The [design system](../design/system/index.html) is the design, written from the built
app ([ADR 0026](../decisions/0026-design-system-from-the-built-app.md)). Category detail
keeps the owner's section order and the calculations in
[ADR 0015](../decisions/0015-category-detail-insights.md). Recibo's status is factual
under ADR 0012 BG1 (SELLO-061); [ADR 0012](../decisions/0012-budget-guidance-contract.md)
tracks the remaining financial inputs.

## Ticket handoffs

What each ticket changed is in its pull request, linked from the ticket file
`docs/tickets/<epic>/SELLO-0NN.md`. This file does not repeat them.

## Remaining work

- The candidate waits on the owner, as the [candidate checklist](../release/candidate-checklist.md)
  lists: `scripts/check --release` with the owner's key (the signed APK and its facts),
  the walk on the owner's phone, and the go/no-go. Distributing it is a separate,
  owner-authorized action. No release was published, signed with the owner's key or
  tagged; the only tag, `design-system-v1`, marks E32's design, not a release.
- E33 and E34 (everyday features, #410) are open. Dropped on 2026-10-29: SELLO-043 (carryover release), SELLO-113
  (the `0.2.0` release) and E14 with SELLO-074 (manual TalkBack, Switch Access and
  keyboard walks). The automated accessibility checks from SELLO-033 stay.
- Open GitHub issues are not tickets.

All epics live under [docs/tickets](../tickets/README.md).

## Detail references

- [Architecture](../../ARCHITECTURE.md), [current decisions](../decisions/0001-mvp-contract.md) and [design guide](../design/README.md).
- [Storage](storage.md), [backup format](../data/backup-format.md), [command recovery](commands.md) and [monthly snapshots](monthly-snapshot.md).
- [Setup](setup.md), [device-test notes](execution-guide.md#device-test-notes) and [quality flow](quality-gates.md).
- [Post-MVP roadmap](../tickets/README.md).
