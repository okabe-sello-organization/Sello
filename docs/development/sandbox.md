# Debug sandbox and financial time

The debug app (`com.software.sello.debug`, "Sello Debug") can run on a separate
**sandbox** with its own data and a financial date you control. It is for exploring
time-sensitive behaviour (rollover, month ends, closed months) through the real app.
Release builds contain none of it. A sandbox starts empty or from a scenario.

## Use it

1. Open **Sello Herramientas**, the debug build's second launcher icon.
2. **Entrar al sandbox** waits for any financial write in progress, then restarts the
   app on the sandbox. Every app screen shows an orange badge in the status bar strip:
   `SANDBOX · 22 oct 2026 · America/Bogota`.
3. In the tools: **+1 día**, **+1 mes**, **Ir a la fecha** (`AAAA-MM-DD`) and **Avanzar
   solo**, one financial day every 2 real seconds, stopping by itself after 31 days.
   **Pausar** stops it. A new process starts paused.
4. **Volver al perfil normal** restarts the app on the normal debug data, which the
   sandbox never touches. The sandbox keeps its data and date for next time.
5. **Reiniciar sandbox**, after a confirmation, erases only the sandbox's data and
   opens it again on today's date.

Android gives touches in the status bar strip to the system, so the badge can't be
tapped with a finger; TalkBack can activate it. Open the tools from their launcher.

## Scenarios

The full corpus, with expected values and replay steps, is in
[sandbox-scenarios.md](../testing/sandbox-scenarios.md). The tools' **Escenarios** section replays a synthetic history through the app's own
commands, so Recibo and Resumen grow exactly as they would by hand.

| Scenario | Steps | Shows |
| --- | --- | --- |
| `g01@1` · G01 | 7 | 1–3 Oct 2026: cumulative 10.000/25.000/30.000, 70.000 left of 100.000; income 200.000, so 170.000 net and 100.000 unassigned |
| `fin-de-mes@1` · Fin de mes y de año | 12 | Comida over its 50.000 on 31 Oct, a new month on 1 Nov, Ocio with no limit, 31 Dec → 1 Jan |
| `seis-meses@1` · Seis meses | 67 | May–Oct 2026, a salary and six seeded expenses a month: the month-by-month columns grow |
| `bordes@1` · Bordes | 10 | A double submit writes once; an edit of an old version is refused (`VersionConflict`); a deletion replaced by a later one cannot be undone (`UndoExpired`); an amount past the maximum never reaches a command (`AboveMaximum`) |
| `semana-fuera@1` and the other *Programados* | 6–22 | Schedules, catch-up after a date jump, batches of 50, month ends, twice a month, pause, changes from tomorrow, archive, deletion; listed in the corpus |
| `inversiones-pagina@1` and the other *Inversiones* | 9–42 | The v2 page's four investments over time, the carried value, USD and the rate, corrections with Deshacer, no value; listed in the corpus |

A step may expect a rejection (`Expect`); meeting it is a pass, anything else stops.

- **Cargar** (load), after a confirmation, erases the sandbox and restarts it at the
  scenario's start date and zone. Loading it again replays it from the beginning.
- **Paso** runs one step; **Reproducir** runs one every 2 real seconds until the end,
  a stop or **Pausar**. The tools show "Paso N de M" and each step's action, result,
  revision and operation ID.
- Every step's operation ID is derived from `id`, `version`, seed and step number, so
  a step run again (after a crash, or after a commit whose checkpoint was lost)
  returns its original receipt instead of writing twice. Progress advances only once
  a step's result is known; a rejection or unknown outcome stops and is shown.
- Entries are dated on the sandbox's day: each date step moves the clock first, so
  entry validation (no future dates) stays real.
- No step stands for a scheduled entry. A schedule step saves a schedule through the
  app's schedule commands; its entries come only from the app's own catch-up, the
  `CatchUpRunner` that runs when Sello opens and when the financial day changes (no
  background work, ADR 0017). A date step changes the day, so Sello starts a catch-up
  by itself; the **Ponerse al día** step after it runs one more and waits, so nothing
  races the next step. It shows the batches committed since the last one ("85
  registradas en 2 lotes: 50 …, 35 …"); a failed or unknown batch stops the scenario.
- A recurrence check reads what a schedule recorded through the app's reads, by each
  entry's origin, and its checkpoint and pause from the stored schedule; a manual entry
  is never counted as an occurrence.
- Investment steps (`Investment`, `Movement`, `EditMovement`, `DeleteMovement`,
  `UndoMovementDelete`, `DeleteInvestment`, `SetRate`) send the app's investment
  commands; an investment check reads the real investment board, each investment's
  figures in its own currency and the overview in pesos with its rate.
- Scenario progress lives in `sandbox-session.xml`, never in Room, so a backup of
  financial data cannot contain it.
- A scenario is Kotlin in `devtools/Scenarios.kt`. Changing its steps or seed means
  a new `version`.

## Faults, operations and the diagnostic

Only in the sandbox, the tools' **Fallos** section arms a fault for the next time it can
fire; each tap adds one, **Quitar fallos** clears them, and a restart or profile switch
drops them because they live only in memory.

| Fault | What happens | What it shows |
| --- | --- | --- |
| Lectura fallida | Recibo and Resumen get one failed read with the last good snapshot | The error with the last good figures, never zero; retry reads again |
| Respuesta perdida tras guardar | The write commits; its caller is told "unknown" | The screen looks the operation up and finds its receipt; nothing is written twice |
| Interrupción tras guardar | The write commits; its caller's work is cancelled | Recovery by the saved operation when the screen opens again |
| Falla antes de guardar | Nothing is written; the caller is told "unknown" | The lookup finds no receipt. This is a simulated boundary failure: it proves nothing about how a transaction rolls back, which the real-Room command tests in `:data` cover |

**Operaciones** lists this process's writes as their callers were told about them, the
simulated fault if any, and, after **Comprobar en almacenamiento**, what storage holds
through the same receipt lookups the screens use. Nothing here writes or changes a
receipt. **Exportar diagnóstico** opens the share sheet with plain text: the session
(date, zone, scenario, step), the faults armed and each operation's ID, kind, what it
was told and what storage holds. It carries no category name, note or amount.

## Rules it keeps

- **Only the financial date changes.** The tools publish a new date on the
  `FinancialClock`; rollover, snapshots and Recibo recompute through the normal paths.
  Device time, audit timestamps, elapsed time (so the six-second undo), animations and
  timeouts stay real.
- **No rewriting the past.** Any date is allowed until the sandbox saves its first
  financial change. After that the date only moves forward; going back needs a reset.
- **No crossing profiles.** Each switch or reset closes a `WriteFence` (new commands
  never start, running ones finish), saves the choice and restarts the process. No
  screen, draft or pending action of one profile survives into the other.
- **Separate files.** Normal: `sello.db`, `sello-preferences`. Sandbox: `sandbox.db`,
  `sandbox-preferences`. The session lives in `shared_prefs/sandbox-session.xml`.
  `eraseFinancialStorage` refuses the normal names.

## How it is built

- `composition/Variant.kt` (main) declares `VariantComposition`. The release source
  set returns `ProductionComposition`; the debug one returns `DebugComposition`, which
  picks the storage names from the session, wraps the three command ports in
  `Fenced*Commands`, and in the sandbox binds a `ControlledFinancialClock`.
- `devtools/` (debug only): `SandboxSession` and its store, `WriteFence`,
  `SandboxTime`, `SandboxProfiles` (enter, leave, reset, load, restart),
  `Scenario`/`Scenarios`, `ScenarioRunner`, `DebugFaults`, `Faulty*` wrappers,
  `OperationLog`, `OperationInspector`, `SandboxBadges` and `ToolsActivity`. In the
  sandbox each write meets its fault first, then the fence. Catch-up goes through the
  fence too (`FencedScheduleCatchUp`); in the sandbox `LoggedScheduleCatchUp` keeps
  each batch's answer in a `CatchUpLog` for the scenario steps, and the runner gets the
  graph's own `CatchUpRunner` and schedule ports (`ScenarioSchedules`) and its fenced
  investment commands and investment reads (`ScenarioInvestments`).
- `VariantComposition.newDataZone` lets a loaded scenario create the sandbox in its own
  zone; release returns none.
- `:app:assembleRelease` is finalized by `verifyReleaseHasNoDeveloperTools`, which reads
  the release APK's dex files, manifest and resources for any devtools class, the tools
  activity or a sandbox string. `-Psello.inspectApk=build/outputs/apk/debug` points it
  at the debug APK, where it finds them.

## Before running device tests

The app's device tests expect the normal profile. Leave the sandbox (or uninstall the
debug app) first; otherwise the test process opens `sandbox.db`.
