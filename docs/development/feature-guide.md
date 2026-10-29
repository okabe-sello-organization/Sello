# Feature guide

How to build or change a Sello feature. [AGENTS.md](../../AGENTS.md) owns the process
and [ARCHITECTURE.md](../../ARCHITECTURE.md) the contracts; this guide shows where the
files go and which shared parts to use. The worked example is **Metas (goals)**: every
layer has a real file, listed in the first table. If this guide and the code disagree,
trust the code and fix the guide.

## 1. The shape of a feature

| Module | What goes there | Metas example |
| --- | --- | --- |
| `:domain` | Model and value types, the policy (rules, pure), the port: commands, `<Feature>Rejection`, reads | `domain/.../model/Goals.kt`, `policy/GoalPolicy.kt`, `port/GoalCommands.kt` (commands, `GoalRejection`, `GoalCommands`, `GoalReads`) |
| `:data` | Entities, DAO, mapper; `Room<Feature>Commands`; `Room<Feature>Reads`; one factory line in `FinancialStorage` | `local/entity/GoalEntities.kt`, `local/dao/GoalDao.kt`, `mapper/GoalMapper.kt`, `repository/RoomGoalCommands.kt`, `RoomGoalReads.kt` |
| `:app` | `feature/<name>/`: State, Action, ViewModel, render model, Root and Screen; labels | `feature/goals/`: `GoalMovementState.kt`, `GoalMovementViewModel.kt`, `GoalsRenderModel.kt`, `GoalMovementScreen.kt` |
| `:app` navigation | Route types in `navigation/Routes.kt`; their `composable<…>` in `navigation/<Feature>Destinations.kt` | `GoalMovementRoute`; `GoalDestinations.kt` |
| `:app` composition | `single<Port> { storage.… }` in `StorageModule`; a `ViewModel` `initializer` in `PresentationModule` | `GoalCommands`, `GoalReads`; `GoalMovementViewModel` |
| `:design-system` | Only visuals a second feature needs: tokens, paper, inputs, charts, feedback | `component/Goals.kt` |

All paths are under `src/main/{java,kotlin}/com/software/sello/` of the module.

- **Direction.** `:domain` knows no Android. `:app` imports `:data` only from
  `composition`. A feature imports no other feature's package; what two features share
  goes to `presentation/` (such as `submission/`, `money/`, `corrections/`) or to
  `:design-system`. `scripts/quality/architecture.py` enforces this.
- **Money commands** return `CommandOutcome<C, F>`: `Committed(value, replayed)`,
  `Rejected(CommandRejection<F>)` or `OutcomeUnknown(operationId)`. `CommandRejection`
  holds the reasons every feature shares (`StaleGeneration`, `OperationConflict`,
  `VersionConflict`, `StorageFailed`) and `Specific(reason)` for the feature's own
  `<Feature>Rejection`. Do not invent a second result type. Each feature has a
  `find(operationId)` to ask what an operation did.
- **Commands in `:data`.** Each command class implements the port and writes inside
  `database.runOutcome(operationId) { … }`, which runs one transaction that cannot be
  cancelled halfway and tells a rollback, an unknown commit and a replay in the shared
  words (`repository/CommandOutcomes.kt`, `CommandProtocol.kt`). Inside it,
  `proceedOr(…)` does the generation and replay checks, and `commitCommand(…)`
  advances the revision and stores the receipt last. Copy the order of
  `RoomGoalCommands`; see [commands](commands.md).
- **Reads in `:data`.** A one-shot read uses `reading { … }`. A live read is
  `database.observeOnRevision { read() }` (it re-reads after every committed command and
  emits `ReadState`); use `observeOn(changes, …)` for another trigger.
  `RoomGoalReads.observeBoard` is the example.
- **Wire it.** Add the class to `FinancialStorage`, then `single<…>` in `StorageModule`
  and the `initializer` in `PresentationModule`. `ProductionGraphTest` checks the graph.

## 2. Money

- `Money` is checked whole minor units: a `Long` plus currency (COP pesos, USD cents).
  `plus` and `minus` return `Outcome<Money, MoneyError>`; overflow and currency mismatch
  are typed errors, never wrapped. For a long sum use `ExactTotal`, which widens and is
  checked once. A recorded amount is a `TransactionAmount` (1–999,999,999,999 COP).
- `BigDecimal` only for rates, percentages and conversions, with the `RoundingMode` named
  in the policy (`HALF_UP` unless it says otherwise) and one rounding at the destination
  scale. See `InvestmentPolicy`. Never `Double` or `Float` for money.
- Validate the original input, not a repaired copy: `1e3` is refused, not turned into
  `13`. The ViewModel builds the value type (`TransactionAmount.of`, `GoalName.of`, …) and
  the port only takes valid types, so an invalid amount cannot reach storage.
- A failed read is never zero. Totals, charts and render models take `ReadState`, not
  `0`. See [money, dates and text](money-dates-text.md).

## 3. Writes

A money write is a command with an `OperationId`, the `generation` it was prepared
against and, for an existing record, its `expectedVersion`. The ViewModel never calls a
command directly; it sends through `Submission`
(`presentation/submission/Submission.kt`):

1. `submission.send(commands::find, step) { operation -> commands.submit(Command(operation, …)) }`
   makes the ID, saves it in the `SavedStateHandle` before the command leaves, and sends
   nothing while another send is in flight or an earlier operation is unresolved.
2. `Committed` and `Rejected` end the attempt. `OutcomeUnknown` asks `find` with the **same**
   ID; until that answers, the operation stays saved (`pending`) and the form asks again with
   `submission.recover(commands::find)`. On start, `if (submission.pending) recover()` settles
   an attempt a process death interrupted. Never send a new ID for an unresolved one.
3. Cancellation is rethrown and leaves the operation saved: it proves nothing was rolled back.
4. The result is a `Settled`. Show a success stamp or celebration only when
   `Committed.found` is false: a commit learned by asking was not seen, and a replay
   (`replayed`) is never celebrated again.
5. The result lives in State (`closed`, `error`, `busy`), never in an event.

`GoalMovementViewModel.submit` and `settle` are the example. Keep the draft in
`SavedDraft(saved, KEY, Draft.serializer())` so it survives process death, and clear it
when the write commits.

**Checklist for a money, storage or recovery change** (AGENTS.md, Tests). Cover each, or say
in the pull request why it does not apply: overflow, invalid input, double submit, stale
version, cancellation or unknown commit, failed read, restart or process death, rollback.
The shared command paths are already tested once in `data/src/androidTest/.../CommandOutcomesTest.kt`
and `CommandRunTest.kt`; a feature's contract test (`GoalCommandContractTest`) covers its own rules.

## 4. Reads

- A port's live read is a `Flow<ReadState<T, E>>` (`domain/.../port/ReadState.kt`):
  `Loading`, `Ready(value)` (which includes a true empty) and `Failed(failure, lastGood)`.
- The render model (`GoalsRenderModel`) turns it into State: `Ready` is the figures;
  `Failed` with `lastGood` is the same figures marked `stale` with a notice and a retry;
  `Failed` with no `lastGood` is the error screen. It never turns a failure into zero or empty.
- Related figures come from one read at one revision: read them in one transaction
  (`database.withTransaction`, as `RoomGoalReads.board`). A damaged row fails the read; it is
  never left out.
- The ViewModel keeps the flow in a `stateIn(…, SharingStarted.WhileSubscribed(5_000), …)`
  and a retry counter with `flatMapLatest` (`MetasViewModel`). Commands move the profile
  revision, so every reader reloads by itself; no refresh calls.

## 5. Screens

- **Pattern.** An immutable `@Immutable` State, a sealed `Action`, a ViewModel that
  exposes `state: StateFlow<State>` and `onAction`, and two composables: `<Name>Root`
  (collects state, owns lifecycle, back and navigation) and `<Name>Screen(state, onAction)`
  (stateless). No `Event` type and no channel: a result the screen must act on is a State
  field, and Root reacts to it (`LaunchedEffect(state.closed) { if (state.closed) onClosed() }`).
  Local `remember` state is for ephemeral visuals only.
- **Required states.** Loading, empty, error (nothing ever loaded) and stale (failed
  read with last good figures), busy (controls held while saving), restored drafts, and
  2× font. A screen test and a screenshot at font scale 2 cover the last.
- **Copy** is in `app/src/main/res/values/strings.xml`, never a literal in code.
  Amounts use `MoneyFormatter` and `MoneyTextValue`; keep the full exact amount.
- **Components.** Reuse a design-system component when one fits. Otherwise build it in the
  feature (`GoalComponents.kt`); move it to `:design-system` when a second feature needs it,
  with its own screenshot test.
- **Themes.** A theme is data. To add one, follow the recipe in
  [layout-and-charts](layout-and-charts.md#themes); never branch on a theme in a screen.
- **Look and layout.** A visible change gets a mock for approval first (AGENTS.md).
  Design is [Sello v2](../design/v2/README.md). Reduced motion and tablet/landscape are
  separate tasks unless the ticket asks.

## 6. Schema changes

A new column, table or constraint needs, in the same pull request: the entity change and
the raised `version`; the exported `N.json` that the build writes (never edit it); a
`Migration` registered in `openSelloDatabase`, no destructive fallback; a real-Room test
that opens the old version with rows and shows they survive
(`ExportedSchemaOnDeviceTest`, and `ExportedSchemaTest` on the host); and a check of the
backup format, bumping its `version` if keys or rules change. Steps:
[storage](storage.md#changing-the-schema) and [backup format](../data/backup-format.md#changing-the-format).
A schema change is the owner's to merge.

## 7. Tests per layer

Test at the cheapest layer that proves it ([quality gates](quality-gates.md)).

| Layer | Where | What |
| --- | --- | --- |
| Domain policy | `domain/src/test/kotlin/…/policy/` (`GoalPolicyTest`) | JVM. Expected money is a literal worked by hand, ideally from the examples in `docs/testing/` |
| ViewModel | `app/src/test/…/feature/<name>/` (`GoalMovementViewModelTest`) | JVM with hand-written fakes and a controlled clock; assert State after each Action |
| Screen | same folder (`GoalMovementScreenTest`) | JVM, Robolectric: `@RunWith(AndroidJUnit4::class)`, `createComposeRule()`, `rule.showSello(Look(…)) { Screen(state) {} }`, from State given by hand |
| Screenshot | same folder (`GoalMovementScreenshotTest`) | Roborazzi: `rule.captureStill("src/test/screenshots/<name>-${look.label}.png")`, Cobalto light, Cobalto dark at font 2, Violeta light |
| Storage | `data/src/androidTest/…/data/` (`GoalCommandContractTest`, `GoalStorageTest`) | Real Room on a device: atomicity, replay, rollback, restore |
| Device journey | `app/src/androidTestDebug/` | Critical paths only. A feature adds none by default; open a task for it |

- **Fakes** are named `Hand<Port>` and live in `app/src/test`: `HandFinancialClock`,
  `HandMonotonicClock`, `HandCategoryReads` (`navigation/ShellFakes.kt`);
  `HandExpenseCommands`, `HandExpenseReads`, `HandIncomeCommands`, `HandIncomeReads`
  and the id helpers `uuid`, `pesos`, `categoryId` (`feature/expense/ExpenseFakes.kt`);
  `HandGoalReads`, `HandGoalCommands` (`feature/goals/GoalsViewModelTest.kt`);
  `HandInvestmentReads`, `HandInvestmentCommands` (`feature/investments/HandInvestments.kt`);
  `HandScheduleCommands`, `HandScheduleReads` (`feature/recurrence/ScheduleFakes.kt`);
  `HandBackupExports` and others (`feature/backup/BackupFakes.kt`). Reuse them; add a fake
  for a new port beside its feature tests. Tests use JUnit 4 and `kotlinx-coroutines-test`.
- **Record screenshots** on Linux, with `./gradlew :app:recordRoborazziDebug --no-build-cache --rerun-tasks`;
  verify with `./gradlew :app:verifyRoborazziDebug`. The same tasks exist for
  `:design-system`, which captures with `drawnBitmap().captureBaseline(path)`.
  Commit the changed images: the diff is the review.
- **Run while working:** `scripts/check domain app`, or `scripts/check app --tests '*GoalMovement*'`.
  Device work: `scripts/emulator start` makes your own API-30 emulator and prints its serial;
  set `ANDROID_SERIAL` and run `scripts/check --device`; `scripts/emulator stop` removes it.
  Never use a running emulator or a phone.
- No sleeps, network or test-order dependence. A flaky test is a defect: fix its cause,
  never retry, skip or weaken it.

## 8. Sandbox

Add a scenario only when it helps the owner try a **new flow or failure** by hand. Prefer
extending a rich history (`app/src/debug/.../devtools/Scenarios.kt`, listed in
[sandbox scenarios](../testing/sandbox-scenarios.md)) with steps of your feature, such as
goals in the goals history, over a new one. A scenario runs the real commands with
synthetic data and a controlled clock. Never add one per rule, and nothing debug-only
outside `src/debug`. See [sandbox](sandbox.md).

## 9. Before you open the pull request

- [ ] The ticket's acceptance criteria are met, with no unrelated cleanup. Unrelated bugs
      you saw are GitHub issues labelled `bug`.
- [ ] `scripts/check domain app data design-system` (the modules you touched) is green, and
      `scripts/check --fast` passes (tickets, links, architecture).
- [ ] Money, storage or recovery change: the checklist in section 3 is covered, or the pull
      request says which items do not apply.
- [ ] Visible change: screenshots re-recorded, and the mock was approved.
- [ ] Schema change: migration, exported schema, retention test and backup check (section 6).
- [ ] Review category: money rules, storage or schema, backup, recovery, privacy, CI or gates
      get an adversarial review by a separate agent ([orchestration](orchestration.md)); other
      code gets a lighter review by a separate agent, never the author.
- [ ] The pull request follows the [template](../../.github/pull_request_template.md): what
      changed, why, the proof (tests, screenshots) and **Not covered**. CI's `quality` check
      is the proof that checks passed; never say a run happened that did not.
- [ ] A lasting decision has an ADR in `docs/decisions/`.
