# SELLO-015 — Introduce the app shell, month session and restored navigation

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-005, SELLO-006, SELLO-009, SELLO-004
- **Gate:** G2

### Outcome
Create stable navigation/month ownership before screens introduce divergent session state.

### Deliverables
Typed routes, SelloScaffold and lifecycle-aware Roots; one shared month session,
validated draft/deep-link entry plumbing, adaptive panels/insets and back policy.
Prepare tab restoration; only Recibo is exposed until real Resumen lands.

### Acceptance criteria
- No Patrimonio, widget, recurrence or disabled Resumen destination. Back closes
  a sheet/detail before returning to Recibo/exit; completed tabs later preserve stacks.
- Month cannot exceed current month. More than 30 minutes background resets month,
  not drafts; financial clock and monotonic inactivity timer have distinct ownership.
- Deep links only validate/prefill; they never create money. Unknown category stays
  unselected. Keyboard hides dock/tab bar and does not cover focused content.

### Tests
Root/session tests for time boundaries, process restoration and malicious/invalid
deep links; device back/insets/keyboard tests across compact and expanded windows.

### Working checkpoint
App launches an honest first-run Recibo scaffold with no fake financial success.
Catalog remains independently usable; G2 passes.

### Context and starting points
Navigation and month state otherwise become scattered per-screen globals. Use the
clock ownership from SELLO-005 and adaptive primitives from SELLO-009. Read
[global structure](../../design/sello-spec.html#navigation), architecture §6 and
[restoration procedure](../../development/execution-guide.md#device-and-process-restoration).

### Implementation plan
1. Define app-owned typed destination IDs/session state, initial Recibo scaffold and
   Root boundary for navigation/events; rendering receives immutable state/actions.
2. Implement one month selection shared by consumers, validated against the financial
   date; save small selection/draft IDs in SavedStateHandle rather than whole records.
3. Define back/reselection/detail/sheet behavior and retained future tab stacks; expose
   only implemented Recibo now. Wire actual Resumen tab when SELLO-022 passes.
4. Use monotonic background inactivity to reset month after >30 minutes; financial
   clock changes update the current-month bound without silently clearing drafts.
5. Add validated deep-link parsing and responsive/inset/keyboard handling. A prefilled
   entry request waits for the implemented form; it never writes or fakes a receipt.

### Concrete cases and pitfalls
- At exactly 30 minutes selection remains; over 30 it resets without clearing typed
  amount. Advancing sandbox financial time must not simulate background inactivity.
- Back closes expanded side sheet, then detail selection, then returns tab root/Recibo.
- Unknown/malicious category/amount in a link stays invalid/unselected. Never pass
  a financial object or success result in a route as a substitute for repository lookup.

### Verification recipe
Create/run `*MonthSessionTest`, `*NavigationStateTest`, `*DeepLinkDraftTest`; run app
connected Root/back/keyboard tests and G2. Follow the guide for eligible process death,
not just rotation, and record compact/landscape/expanded behavior.

### Execution progress
2026-10-10: started on branch `sello-015-app-shell` after SELLO-014 merged and E03 closed.

Delivered in `:app`: typed route and tab list, `MonthSession`, `ShellViewModel` with
restoration, the back order, `EntryLinks`, `SelloAppRoot` and `ShellScreen` on the
design system's scaffold with an adaptive month picker, a minimal honest Recibo fed
by the monthly snapshot, and one factory for view models. The greeting is gone.
`docs/development/app-shell.md` states the contracts for the screens that follow.
In `:design-system`, a device test for the keyboard hiding the dock and tab bar,
which had none.

Dependencies added to `:app`, all at versions that leave every existing pin as it
was: Navigation Compose 2.9.8 (the architecture's default engine; 2.10 was not used
because it would force newer Compose, lifecycle and activity versions than those
pinned), lifecycle `runtime-compose`, `viewmodel-compose` and `viewmodel-savedstate`
at the 2.9.4 already resolved, and the Kotlin serialization plugin with
`kotlinx-serialization-core` 1.11.0, which typed routes need. Only `app/gradle.lockfile` changed.

Choices made during the work, for owner review:
- No tab bar with a single tab, and no "Anotar" dock until the entry form exists
  (SELLO-017). A button that opens nothing would be a fake.
- Recibo already reads the real monthly snapshot instead of showing fixed text, so
  what it says is true: empty explanation, exact total, or error with retry.
  SELLO-018 replaces this minimal content.
- The month follows the current month across a month change unless an earlier
  month was picked. Picking the current month goes back to following.
- "In the background" means the screen is no longer visible. Returning after a
  device restart counts as a long absence.
- The 30-minute rule also holds when the app was killed in the background: the
  moment it left is saved with the selection.
- The entry link is `sello://anotar?categoria=…&monto=…`. It is parsed and kept as
  a pending request, but the manifest does not declare it yet: declaring it before
  the form exists would let other apps open Sello on a link that does nothing visible.
- A link that is not a valid address, or not exactly that shape, is ignored whole.
  Inside a valid link, an invalid amount or category is dropped and the rest kept.
- The month picker is a bottom sheet, and a side panel in a window wide enough for one.
  It goes back as far as the year 2000.
- View models are built by one factory in the composition package instead of adding
  Koin's Android artifact, which keeps Koin out of the screens as the architecture
  rule requires.

Found on the way: on the real process-death check the picked month first seemed
lost. The app was right; the check had returned to it with a different kind of
launch than it was started with, which makes Android open a second copy. Repeated
through the launcher both times, the month and the open picker were restored.

Left for later: the dock, the entry form and the link declaration (SELLO-017);
first-run category creation and its button on the empty Recibo (SELLO-016); the
real Recibo content (SELLO-018); the Resumen tab (SELLO-022); showing a database
that cannot be opened at startup as a screen instead of a crash, noted in SELLO-011,
which still needs an owner: it fits this shell and is small, but was not in this
ticket's acceptance.
