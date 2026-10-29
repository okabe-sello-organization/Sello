# Independent executor guide

Read this with the assigned ticket, [agent rules](../../AGENTS.md),
[architecture](../../ARCHITECTURE.md), and [tickets and gates](../tickets/README.md).
It supplies shared tools and conventions; the ticket supplies the actual work.
Do not execute all tickets at once or copy a proposed API blindly over completed code.

## Starting from a new checkout

1. Run `pwd`, `git status --short`, `git log -1 --oneline`, and inspect applicable
   `AGENTS.md`. Preserve existing work.
2. Read the ticket's dependencies and their Delivery evidence. Locate their
   **actual** produced contracts, tests and decision records. If a supposedly Done
   prerequisite is missing/broken, report the discrepancy; do not recreate a fake
   adapter just to unblock yourself. Ready work still requires its listed approvals.
3. Make an acceptance-to-change/test checklist and identify owned files before
   editing. Prefer the established names and packages below to new ones.
4. For UI work, open [Sello v2](../design/v2/README.md) and its
   [component catalog](../design/v2/components.html). A ticket's v1.1 anchors apply
   only to screens v2 does not show (ADR 0013). Never infer money from mockup figures.
5. Use the [setup](setup.md) and
   [toolchain decision](../decisions/0005-toolchain.md). Record unavailable
   devices/keys/approvals as blockers where acceptance requires them; never waive
   the gate.

## Module and file map

| Ownership | Where it lives |
| --- | --- |
| Pure financial types/policies/ports | `domain/src/main/kotlin/com/software/sello/domain/{model,policy,port,usecase}/`; tests in `domain/src/test/kotlin/` |
| Room/transactions/codecs | `data/src/main/java/com/software/sello/data/{local,mapper,repository}/` and `data/src/main/kotlin/.../data/{backup,export,preferences,reset}/` (backup codecs, CSV/JSON export, DataStore preferences, reset); host tests in `data/src/test/`, real Room in `data/src/androidTest/`; exported schemas in `data/schemas/` |
| Reusable UI | `design-system/src/main/java/com/software/sello/designsystem/{component,icon,theme}/`; previews and host/device tests alongside that module |
| App wiring/platform | `app/src/main/java/com/software/sello/{composition,navigation,platform,presentation}/`; composition is the only app package importing data implementations |
| Feature UI | `app/src/main/java/com/software/sello/feature/{backup,category,expense,goals,income,investments,recibo,recurrence,reset,resumen,settings,startup}/` (recurrence holds the schedules); each owns State/Action/ViewModel/ScreenRoot/Screen as needed (no Event types; results stay in State) |
| Sandbox | `app/src/debug/java/com/software/sello/devtools/`, `app/src/debug/res/` and debug-only bindings; release has no reverse dependency |
| Catalog | `catalog/src/main/java/com/software/sello/catalog/{components,foundations}/`; synthetic render examples only, no domain/data/app dependency |
| Decisions, tickets and runbooks | `docs/decisions/`, `docs/tickets/<epic>/<ticket>/`, `docs/testing/`, `docs/release/` |

Port/type names in tickets are contract suggestions. Agree signatures at the owning
boundary, document them in its tests, and reuse them downstream. Do not create a
second “almost equivalent” repository or calculation because a name differs.

## Tools and host verification

Tools and the environment are in [setup](setup.md). Run commands
from the repository root. Use `rg` for source searches. Android Studio is useful for
previews/AVDs; it is not a substitute for command evidence.

Focused **host** examples:

```bash
./gradlew :domain:test --tests '*MoneyPolicyTest'
./gradlew :app:testDebugUnitTest --tests '*ExpenseFormViewModelTest'
./gradlew :catalog:assembleDebug
```

The test names specified by tickets are **tests to create**; if compatible predecessor
tests already exist, document the actual selector. A no-matching-tests result is
not a pass. For a no-test configuration/docs change, verify the actual config/links
and run its gate instead of fabricating behavior red→green.
G0/G1/G2/G3 commands are in [README.md](../tickets/README.md#gate-profiles). Run the assigned
gate after focused tests. Default ticket commands run the host gate G1; the hosted
`quality` check runs the device suites on the merge result. `--affected BASE` runs
the documented dependency-aware device scope locally; an explicit `--gate G2` runs
every device suite. Do not replace gate evidence with a focused task.

## Device and process restoration

Use the API 30 emulator from [setup](setup.md#device-smoke) or an
explicitly selected device. If
T3 device tools are available, list/open a suitable device first; otherwise use
Android Studio's AVD manager and `adb devices`. Set `ANDROID_SERIAL` when multiple
devices exist. Device permissions follow [AGENTS.md](../../AGENTS.md#tests).
Release QA uses an isolated synthetic test installation; sandbox scenarios use debug only.

```bash
adb devices
./gradlew :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest
./gradlew :data:connectedDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :catalog:connectedDebugAndroidTest
```

For an instrumented class selector use the actual configured test package:

```bash
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.software.sello.data.ExpenseCommandContractTest
```

- **Rotation/activity recreation:** exercise layout and SavedStateHandle/ViewModel
  restoration, with exactly one financial write after repeat composition.
- **Eligible process death:** enter a draft, background via Home, kill the **debug**
  background process using `adb shell am kill com.software.sello.debug`, then return
  through Recents. Verify the process really died, task remained, and draft/operation
  ID restores.
- **Cold start/force-stop:** test persistence/recovery separately. Force-stop or
  clearing app data is not equivalent to saved-task process restoration.
- **Room reopen:** close/reopen the real database in tests, preserving the file;
  in-memory fakes cannot prove durable receipts/migration/rollback.
- **Visual evidence:** record build, API, window size, theme/font scale and scenario.
  A screenshot confirms the shown state, not all financial/device acceptance.

### Device test notes

- Wait for observable UI/data conditions with the existing `Waits.kt` helpers;
  asynchronous updates need condition-based waits, not immediate reads or sleeps.
- After typing, use the `press()` semantics helper and hide the keyboard during
  cleanup so it cannot interfere with the next test.
- Tests in one process share database and month state. Reset both using
  `TestData.reset()` and `TestCommands.showCurrentMonth()` where applicable.
  Seed histories through `TestCommands` so normal observers see the writes.
- For a test window larger than the display, check node existence and bounds;
  off-screen content need not satisfy a visibility assertion.
- Device runs cover Android API availability that host-JDK tests cannot prove.
  System dialogs can steal focus and Back presses; inspect focus before diagnosing
  an app failure.
- For saved-task restoration, return with the same launcher intent or through
  Recents. A different launch can create a second task and test a different journey.

## Contract and failure checklist

Commands carry operation ID, current generation and expected version for existing
records. Tests must independently inspect committed rows/receipt/revision, not
assert only a returned boolean. Same ID/same canonical input replays; differing
input conflicts. Rollback checks row **and** receipt absence. After-commit failures
must preserve committed data and recover by ID. Rethrow cancellation.

Read a consistent revision/as-of snapshot, not independently combined stale flows.
Do financial arithmetic with checked exact values; adapt to approximate coordinates
only in chart drawing. Storage/input/backup decoding failures remain typed failures.
No transient follow-up error may invalidate an already committed financial write.

Write real-Room rollback tests by provoking a real transaction constraint/failure;
use debug boundary decorators only for explicitly simulated read/caller effects.
No timing sleeps or swallowed assertions. Ports for file/provider failures make
write, close, canceled picker and preference-pending outcomes testable.

## Independent example corpus

See [ADR 0001](../decisions/0001-mvp-contract.md) for policy and the
[example corpus](../testing/mvp-financial-examples.md) for exact examples. Table
values are independent assertions, not expected results generated by calling the
production calculator. Never turn a pending example into hidden product policy.
The rules approved in [ADR 0012](../decisions/0012-budget-guidance-contract.md)
need their own independent examples before the D03 ones they replace are retired.

## Delivery record

Verification commands, the executor flow, what makes a report stale and how to
recover from each failure are in the [quality gates guide](quality-gates.md#quality-runner).
The evidence fields, the ticket folder and the definition of done are in the
[tickets README](../tickets/README.md#definition-of-done--every-ticket). Do not
commit or publish unless the current user request authorizes it.

## Source library

These are official integration guides, not pinned API/version guarantees. Read the
relevant guide against the selected versions; consult predecessor contracts first.

- Android architecture/state: https://developer.android.com/topic/architecture/recommendations
- Build/toolchains: https://developer.android.com/build/migrate-to-built-in-kotlin
  and https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria
- Build variants: https://developer.android.com/build/build-variants
- Typed navigation: https://developer.android.com/guide/navigation/design/type-safety
- Compose testing/restoration: https://developer.android.com/develop/ui/compose/testing
  and https://developer.android.com/develop/ui/compose/state-saving
- Adaptive UI/accessibility: https://developer.android.com/develop/ui/compose/layouts/adaptive
  and https://developer.android.com/develop/ui/compose/accessibility
- Room/migrations: https://developer.android.com/training/data-storage/room
  and https://developer.android.com/training/data-storage/room/migrating-db
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Documents/privacy: https://developer.android.com/training/data-storage/shared/documents
  and https://developer.android.com/identity/data/autobackup
- Koin constructors/graphs: https://insert-koin.io/docs/reference/koin-core/definitions/
- Coroutine tests: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
- Serialization: https://kotlinlang.org/docs/serialization.html
- Formatting: https://pinterest.github.io/ktlint/latest/
- Shrinking/signing: https://developer.android.com/build/shrink-code
  and https://developer.android.com/studio/publish/app-signing
