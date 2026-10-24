# Time sources and composition

Rules: [architecture](../../ARCHITECTURE.md) §§2, 4, 9.

## Three clocks, never interchangeable

| Port (`:domain`, `domain.port`) | Meaning | Production adapter (`:app`, `platform`) | May a sandbox replace it? |
| --- | --- | --- | --- |
| `FinancialClock` | Today's date in the financial zone, observable | `SystemFinancialClock` | Yes, and only this one |
| `AuditClock` | Real instant for "when was this recorded" | `SystemAuditClock` | No |
| `MonotonicClock` | Elapsed time for deadlines such as undo | `SystemMonotonicClock` | No |

Financial rules read `FinancialClock.today` and observe it; they do not call
`LocalDate.now()` or any other wall-clock API. `scripts/quality/architecture.py`
rejects those calls outside the `platform` package, along with `GlobalScope`,
global `Dispatchers` and Koin outside the composition root.

## Financial zone

The financial zone is stored in the database's profile row ([storage](storage.md)).
At startup `openStorage` establishes that row: the first start of an installation
stores the device zone, and every later start reads the stored zone and ignores the
device's. `SelloApplication` passes the stored zone to `ProcessFinancialZone`, which
holds it for the process. Do not add a preference or file for it.

## When financial "today" is recomputed

`SystemFinancialClock` recomputes at each midnight of the financial zone and on
every `TimeSignals` change. `AndroidTimeSignals` emits when the app returns to the
foreground and on the system's time, date and time-zone broadcasts. A signal also
reschedules the midnight timer, so a clock moved backwards is handled. A device
zone change triggers a recompute but cannot change the financial zone.

## Ownership and disposal

- `ApplicationScope` (supervisor job on the injected default dispatcher) owns work
  that outlives a screen. The financial clock's observer runs in it.
- Closing the Koin application closes `ApplicationScope`, which cancels the
  observer, stops the midnight timer and unregisters the receiver and lifecycle
  observer. A closed scope is not reused.
- Cancellation is rethrown, never turned into a failed or empty result.
- The debug sandbox binds a `ControlledFinancialClock` after the production modules,
  so the production clock is never created, and audit and monotonic time stay real.
  Switching sessions restarts the process after a write fence closes; see
  [sandbox](sandbox.md).

## Catch-up

`CatchUpRunner` is created with the graph and runs `CatchUpSchedules` in
`ApplicationScope` ([recurrence rules](../testing/recurrence-examples.md), rule 6): once
the schedule book is first read, whenever `FinancialClock.today` changes (midnight, a
return to the foreground on a new day, sandbox time) and after every committed change,
such as a saved schedule. One run at a time; triggers during a run make one more run,
not one each. There is no WorkManager job and no notification: a process that is not
running records nothing until it runs again. Today is read once per run.

## Composition

The `composition` package is the only place in `:app` that references `:data` or
Koin. `PlatformModule` binds the adapters, `StorageModule` the financial storage,
`PreferencesModule` the preference ports and `PresentationModule` builds every view
model ([app shell](app-shell.md)). Every collaborator is a required constructor
parameter and the financial clock is created with the graph, so a missing binding or
an uninitialized zone fails at startup. New workflows are bound when they exist; do
not register a placeholder that reports success.

`ControlledFinancialClock` and the sandbox tools live in `app/src/debug/.../devtools`
and are absent from release builds. `VariantComposition` is the one hook: release
returns `ProductionComposition`, debug a `DebugComposition` that may open the sandbox
([sandbox](sandbox.md)). The normal debug profile runs on production time.

## When the data cannot be opened

`SelloApplication` holds one `Startup`. It opens storage and builds the graph once,
on the main thread, and keeps the result as `StartupState`: `Ready` with the view
model factory, or `Failed` with the storage failure. `MainActivity` shows the app
only when `Ready`; when `Failed` it shows `feature/startup`, whose view model is the
one built without the graph. A link that arrives while failed is dropped.

- `retry()` opens again only while failed, so a second store is never opened beside
  one in use. A retry that works replaces the failure screen with the app.
- `setAside()` moves the unreadable file away and is refused once the data opened.
  After it, `MainActivity` opens again and shows the app in a new task, so nothing
  saved by the old screens is restored onto the empty history.
- Koin is started only inside a successful open, so there is never a graph without
  a stored financial zone.

Startup is deliberately synchronous ([ADR 0008](../decisions/0008-unreadable-storage-start-over.md)).
