# Debug sandbox and financial time

The debug app (`com.software.sello.debug`, "Sello Debug") can run on a separate
**sandbox** with its own data and a financial date you control. It is for exploring
time-sensitive behaviour (rollover, month ends, closed months) through the real app.
Release builds contain none of it. A sandbox starts empty or from a history.

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

## Histories

The tools' **Historias** section loads one of a few rich synthetic histories (a normal
year, a tight month, goals, investments with dollars, fixed payments) in one go, through
the app's own commands and catch-up, so Recibo, Resumen, Metas and Inversiones look
exactly as they would after living through it. Each ends with the financial clock on
its own "today". They and the figures their tests check are in
[sandbox-scenarios.md](../testing/sandbox-scenarios.md).

- **Cargar** (load), after a confirmation, erases the sandbox, restarts it in the
  history's zone and loads every step. The tools show the progress, and the badge says
  "cargando historia" until the last step is confirmed.
- Every step's operation ID is derived from `id`, `version`, seed and step number, so
  a step run again (after the app was closed mid-load, or after a commit whose
  checkpoint was lost) returns its original receipt instead of writing twice. Progress
  advances only once a step's result is known, and the next start carries on a load
  that did not finish. A refusal or an unknown outcome stops it with "Carga
  incompleta" and the reason; **Seguir cargando** tries again.
- Entries are dated on the sandbox's day or earlier: each date step moves the clock
  first, so entry validation (no future dates) stays real.
- No step stands for a scheduled entry. A schedule step saves a schedule through the
  app's schedule commands; its entries come only from the app's own catch-up, the
  `CatchUpRunner` that runs when Sello opens and when the financial day changes (no
  background work, ADR 0017). The catch-up step after each date step runs it once more
  and waits, so nothing races the next step.
- Progress lives in `sandbox-session.xml`, never in Room, so a backup of financial
  data cannot contain it.
- A history is Kotlin in `devtools/Scenarios.kt`. Changing its steps or seed means a
  new `version`.

## Anotar varios: an unknown outcome

In the sandbox the tools' **Anotar varios** section has **Ocultar el resultado del
próximo recibo**. The next receipt of several expenses is really saved, but the sheet is
told its outcome is unknown and the first check fails too: it shows **Aún no sabemos si
se guardó** with only **Comprobar si se guardó**. Pressing it asks again with the same
operation, finds the receipt and closes with the usual confirmation; nothing is written
twice. The switch lives in memory only and is off after a restart.

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
  picks the storage names from the session, wraps the command ports in
  `Fenced*Commands`, and in the sandbox binds a `ControlledFinancialClock`.
- `devtools/` (debug only): `SandboxSession` and its store, `WriteFence`,
  `SandboxTime`, `SandboxProfiles` (enter, leave, reset, load, restart),
  `Scenario`/`Scenarios`, `ScenarioRunner`, `SandboxBadges` and `ToolsActivity`.
  Catch-up goes through the fence too (`FencedScheduleCatchUp`); the runner gets the
  graph's own fenced commands and `CatchUpRunner` (`ScenarioSchedules`).
- `VariantComposition.newDataZone` lets a loaded history create the sandbox in its own
  zone; release returns none.
- `:app:assembleRelease` is finalized by `verifyReleaseHasNoDeveloperTools`, which reads
  the release APK's dex files, manifest and resources for any devtools class, the tools
  activity or a sandbox string. `-Psello.inspectApk=build/outputs/apk/debug` points it
  at the debug APK, where it finds them.

## Before running device tests

The app's device tests expect the normal profile. Leave the sandbox (or uninstall the
debug app) first; otherwise the test process opens `sandbox.db`.
