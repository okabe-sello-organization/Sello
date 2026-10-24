# Sello — architectural guardrails

**Status:** proposed implementation baseline · **Updated:** 2026-10-16

**MUST** is mandatory; **SHOULD** is the default, with a documented reason to
deviate; **COULD** is optional, justified by a shipped capability or measured need.
These rules govern new work; the generated Android template does not yet satisfy
them. This document owns architecture and release scope, not implementation detail.

## 1. Product boundary and delivery

Sello is an offline-first, local-only Android financial app, Spanish first 
(`es-CO`). No account, backend, bank connection, or cloud sync is required.

| Increment | Complete capabilities, not placeholder destinations |
| --- | --- |
| **MVP** | Manual COP expenses/income; categories and month-specific budgets; Recibo/category detail; basic Resumen with actual totals and shared budget verdict/projection; editing and guarded deletion/undo; appearance/order preferences; manual backup/restore and safe reset. |
| **Next** | Metas with contribution history/milestones; Resumen comparisons/records and CSV/JSON exports; Anotar varios; user-configured recurrence, pause, and catch-up. |
| **Later** | Inversiones with a reviewed ledger, benchmarks/targets/accrual; COP/USD/EUR/GBP and manual/provider FX rates; outgoing budget alerts; remaining Sello refinements. |

- **MUST** retain the reference's non-widget capabilities in the roadmap, with
  acceptance examples per increment. Do not prebuild deferred tables/jobs.
- **MUST** retain signed budget carryover (surplus **and** overspending) as a future
  post-MVP requirement, planned in SELLO-E09; optional category/global scope and
  detailed policy remain undecided. MVP renews configured monthly budgets without carryover.
  Preserve MVP history/domain boundaries without dormant carryover infrastructure;
  see [carryover direction](docs/decisions/0002-signed-carryover-direction.md).
- **MUST NOT** implement widgets/Glance, notification ingestion, capture inboxes,
  merchant parsers, or notification-listener services. Outgoing alerts are different.
- **SHOULD** launch with two complete tabs, Recibo and Resumen, then give Metas a
  primary destination when it ships. Add a distinct Inversiones destination when
  investments ship. Prefer three equally clear destinations where feasible;
  **MUST NOT** exceed four or expose unfinished tabs. This replaces the reference's
  three-tab ceiling and mandatory Patrimonio switch; see [ADR 0011](docs/decisions/0011-goal-centered-design.md).
- **MUST** retain the Anotar → persisted operation receipt → Recibo financial
  journey, including recovery, accessibility, and CI. The operation receipt does
  not require a separate confirmation screen; ordinary saves use brief feedback
  in the new design direction. Backup/restore/reset must be complete before MVP release.
- **MUST** build the live-testing sandbox and component catalog alongside the
  first slice, extending their scenarios/examples as each capability ships (§9).
- **MUST** harvest legacy requirements, counterexamples, and reviewed algorithms,
  never copy entire legacy layers or assume its formulas are authoritative.
- **MUST** target private APK distribution for MVP, with the approved SDK 30 floor
  subject to SELLO-002 compatibility verification. Use distinct customer/debug/catalog
  identities from D02; no Google Play/AAB publication work is in current scope.
  Private distribution does not waive production signing, artifact inspection or
  release/device acceptance; distribution requires separate authorization.

## 2. Modules and ownership

Start with four production Gradle modules plus a developer-only catalog, not one
module per screen:

| Module | Owns | Allowed production dependencies |
| --- | --- | --- |
| `:domain` | Typed models, financial policies, meaningful use cases, consumer-shaped repository/workflow ports and platform-neutral application contracts | Kotlin/JVM, coroutines; no Android, UI, storage, DI, or HTTP libraries |
| `:data` | Room schema/DAOs/mappers, migrations, DataStore adapter, portable backup/export codecs, future HTTP adapter; atomic workflow implementations | `:domain`, infrastructure libraries |
| `:design-system` | Sello tokens/theme, stateless paper/input/feedback/chart primitives and previews | Compose/Material/adaptive UI; no domain, data, or feature dependency |
| `:app` | Composition root, navigation/month session, feature presentation, Android document/permission/notification/worker adapters | `:domain`, `:design-system`; `:data` **only from composition** |
| `:catalog` | Separately installable developer app showcasing grouped UI components and synthetic states | `:design-system`, Compose/UI; no `:app`, domain, data, storage, or network dependency |

- **MUST** enforce module direction in Gradle and composition-only `:data` imports
  with an automated architecture check. Features/platform adapters cannot use DAOs,
  entities, concrete repositories, or another feature's internal presentation.
- **SHOULD** organize app packages as `composition`, `navigation`, `platform`,
  `presentation` (shared formatting), and `feature/<capability>`. Financial rules
  belong in domain; storage codecs in data; translated copy/render models in UI.
- **MUST** separate entity/wire/domain/render models where contracts differ.
  Persist stable keys, never translated labels, enum ordinals, or class names.
- **MUST** constructor-inject required collaborators. Koin belongs in composition;
  no service lookup in logic and no production defaults that report fake success.
  Inject clocks, dispatchers, owned scopes, and external ports; never `GlobalScope`.
- **SHOULD** use cases for rules/coordination/reuse, not pass-through wrappers.
  No generic managers/event buses, universal repositories/forms, or event sourcing.
- **COULD** extract feature modules when ownership isolation or measured build
  cost warrants it; they depend on domain/design-system, never data or each other.
- **SHOULD** initially keep sandbox controls/adapters in `:app/src/debug`.
  **COULD** extract `:dev-tools` as they grow; only debug variants may depend on it,
  with data wiring confined to composition. Production modules never depend on catalog.

## 3. Technology policy

| Responsibility | Default choice and constraint |
| --- | --- |
| Build/language | Kotlin, Gradle Kotlin DSL, version catalog; Compose BOM; KSP for Room. **MUST** validate one compatible stable AGP/Kotlin/KSP/JDK/SDK matrix, rather than independently selecting newest versions. |
| UI/state | Jetpack Compose + Material 3/adaptive APIs, AndroidX ViewModel, lifecycle-aware collection; coroutines/Flow. Use Sello styling, not stock Material visual defaults. |
| Navigation | **SHOULD** use Navigation Compose with typed routes and saved tab stacks; one navigation engine. Route IDs, not objects/financial payloads. |
| Persistence | Room for financial truth; DataStore for nonfinancial preferences. Export Room schemas from version 1. |
| DI | Koin constructor wiring at the app boundary; production graph validation. No DI dependency in domain. |
| Serialization | `kotlinx.serialization` with explicit versioned wire DTOs and strict boundary validation. Not serialized Room entities. |
| Background | WorkManager + injected WorkerFactory, **only when** recurrence/alerts ship. Workers call use cases, not DAOs. |
| Network | **SHOULD** use Ktor Client + a maintained Android engine/typed serialization when provider rates ship; no HTTP dependency or INTERNET permission for MVP. |
| Charts | **SHOULD** start with small Compose drawing primitives behind Sello chart contracts. **COULD** adopt a renderer after precision, accessibility, theme, and performance tests—not expose its types to domain. |
| Verification | JUnit, `kotlinx-coroutines-test`, real-Room instrumented tests, Compose UI tests, Android Lint; **SHOULD** use ktlint and Detekt plus architecture rules. |

- **MUST** pin dependencies centrally; review licenses/security/maintenance and
  transitive costs. No dynamic versions; upgrades must pass gates.
- **MUST** audit the generated scaffold before the first slice: its mixed catalog,
  SDK/toolchain choices, enabled platform backup, and unoptimized release are
  starting configuration, not approved decisions. AGP 9 uses built-in Kotlin;
  follow its compatibility guidance rather than adding duplicate Kotlin plugins.
- **COULD** introduce Paging, benchmark tooling, or convention plugins when query
  size, performance, or repeated build configuration actually justifies them.

Official integration references: [architecture][android-architecture],
[typed navigation][navigation], [Room][room], [DataStore][datastore],
[Koin workers][koin-workers], [AGP built-in Kotlin][agp-kotlin]. Exact versions
belong in `gradle/libs.versions.toml`, not this long-lived document.

## 4. Financial contracts

**Design transition:** [ADR 0011](docs/decisions/0011-goal-centered-design.md) approves
retiring pace-based home judgment. SELLO-061 replaced the forecast-based verdict
with ADR 0012 BG1's factual status; the D03 forecast, allowance and worked examples
below otherwise describe the delivered financial contract and remain in force until
explicitly replaced through scoped implementation with approved independent examples.
[ADR 0012](docs/decisions/0012-budget-guidance-contract.md) records approved
factual status/tone and a headline after upcoming-payment reservation,
with the reserved amount at lower priority, planned versus recorded income, and
category-only allocation. An equal reserved-to-recorded payment leaves the headline
unchanged; omit reservation context when none remains. Goal/investment records do
not independently allocate income; users can budget those purposes through categories.
The target zero-budget/zero-spending status is Al día. For an overrun with upcoming
payments still reserved, Revisar takes priority; once recorded, use Excedido if
spending exceeds the limit. Recurrence and expected-income lifecycle details are
deferred beyond MVP. Remaining implementation inputs are listed in the
[open decisions](docs/design/philosophy.md#open-implementation-decisions).

- **MUST** represent money as checked `Long` minor units + currency: COP scale 0;
  USD/EUR/GBP scale 2. Use exact widened intermediate sums and decimal rates with
  explicit units. Round conversion once at destination scale (`HALF_UP` default).
  No Float/Double money, unchecked SQL arithmetic, wraparound, saturation, or
  missing-data-to-zero substitution. Approximate chart coordinates are not money.
- **MUST** validate original input and command/storage/wire boundaries: positive
  amounts where required, currencies, references, dates, versions, and bounded text.
  Reject ambiguous pasted input; `1e3` must not become `13`.
- **MUST** apply [approved D05](docs/decisions/0001-mvp-contract.md#d05--fields-input-grammar-and-income-labels)
  field/name/source rules and plain-digit or correctly grouped-dot COP input grammar.
  Individual MVP expense/income amounts must be 1–999,999,999,999 COP across entry,
  domain commands, storage decoding and backup validation. Reject out-of-range
  records/files, never truncate or repair them. This transaction cap is not an
  aggregate-total cap; checked Long financial totals remain distinct. See
  [transaction-range decision](docs/decisions/0003-mvp-transaction-amount-range.md).
- **MUST** use `LocalDate`/`YearMonth` for effective periods and `Instant` for audit
  time. Persist the financial zone in Room, defaulting to the device zone at setup;
  travel must not rewrite history. Inject time, observe rollover, and exclude
  future entries from actual totals. MVP manual entries cannot be future-dated.
- **MUST** derive related figures from one consistent revision/as-of snapshot;
  UI, charts, alerts, and exports reuse domain calculations, not copies.

| Policy | Required meaning |
| --- | --- |
| Budget | `remaining = month limits − effective expenses`; unlimited-category spending still counts. No limits means “Has gastado / Sin límite”. Preserve each month's limits; later edits must not rewrite prior months. |
| Allowance/projection | Days available include today; allowance is `max(remaining − recurringDue, 0) / availableDays`. Projection is `spent + recurringDue + variableDailyPace × remainingDays`; recurring charges are excluded from variable pace and never counted twice. MVP recurringDue is genuinely zero. |
| Verdict | Current (ADR 0012 BG1): recorded spending only, never pace. Al día below the limit, including a zero limit with zero spending; Al límite at exactly a positive limit; Excedido above it. Past: Cumplido at or below the limit, Excedido above; actuals only, no allowance. Resumen's forecast estimate and actual-spend verdict must be labeled distinctly. |
| Income allocation | `destined = sum(configured finite category limits)`; recorded income minus destined is planning capacity, **not account balance**. Goal/investment records do not independently allocate income. A user-created saving/investment category counts by its limit like any other category. Expenses are not subtracted again. This category-only rule supersedes the previous future contribution sum; see ADR 0012 BG4. |
| Recorded net flow | Monthly recorded income minus recorded expenses; not an account balance including opening funds or unrecorded activity. |
| Goals | Contributions are historical records, not bank transfers. Derive milestone dates from actual crossings; projected dates require explicit assumptions. |
| Investments | Stable `(effectiveDate, logicalSequence)` replay. `totalGain = currentValue + withdrawals + dividends − contributions`; percentage is simple gain, not IRR. Zero contributions yields undefined percentage. Recorded value, accrual, and benchmarks have distinct provenance. |

- **MUST**, while the delivered D03 model is in use, derive variable pace from recorded spending
  divided by elapsed day-of-month including today; projection uses future days
  excluding today and is an estimate even on day 1. Round the displayed daily
  planning allowance **down to multiples of COP 50**, not recorded money or remaining
  budget. Forecast display rounds HALF_UP to one peso; no verdict uses the
  forecast. See [approved D03](docs/decisions/0001-mvp-contract.md#d03--forecast-allowance-and-verdict)
  for widened arithmetic and positive-sub-50 allowance behavior.
- **MUST** compute overall allowance from overall finite limits minus all applicable
  spending, not an individual category's deficit. Preserve category overruns without
  zeroing a still-positive overall allowance or silently reallocating category limits;
  see approved B08 in the financial example corpus.
- Category detail's own planning figures and historical statistics follow
  [ADR 0015](docs/decisions/0015-category-detail-insights.md): exact actuals,
  day-of-month week buckets, a bounded remaining-week allowance and averages of
  completed configured months. These category figures never replace the overall
  allowance or allocate other categories' limits.
- **MUST** distinguish Ingresos menos gastos, Restante del presupuesto and Por
  asignar al presupuesto (MVP income minus configured limits), replacing ambiguous
  Sin destinar wording. Do not subtract expenses again from allocation or call any
  figure a verified account balance/generic Disponible. **SHOULD** emphasize
  remaining/allowance in Recibo and keep allocation clearly identified as planning.
  Resumen's new hierarchy supports historical understanding and adjustments; its
  delivered net-flow figure retains the same financial meaning.
- **MUST** approve worked day-cutoff/short-month/projection examples for any
  forecast still used. The BG1 status replaced the 95%/100% forecast verdict
  (SELLO-061); its boundaries are the limit itself and the zero-limit case. Before investments, separately approve
  annual-rate convention and dated-cash-flow examples; nominal and effective rates
  cannot be mixed. Estimates never silently become recorded financial movements.
- **MUST** distinguish explicit zero budgets from unlimited categories. Allow
  explicit historical corrections without changing unrelated configured limits;
  announce this capability with accessible contextual guidance and show the target
  period/change scope before submission and confirmed completion afterward.
  Signed carryover is deferred beyond MVP; activation details require SELLO-037
  approval. Do not erase historical overspending/underspending records when renewing
  a non-carry MVP budget or imply the renewal resets actual funds.
- **MUST** resolve unopened historical months from configuration effective in that
  month, not today's default or app-open timing. Retain effective-month default
  history and explicit month overrides; do not extrapolate before known configuration.
  Materialization must be idempotent/atomic, without precreating future budgets.

## 5. Persistence, commands, and recovery

- **MUST** use Room transactions, foreign keys, indexes, strict decoding, and tested
  non-destructive migrations. Financial configuration (budgets, zone, rates) and
  recovery metadata belong in Room; DataStore cannot be a second financial truth.
- **MUST** bound/page growing queries and run I/O, ledger replay, and file work
  off main. Related reads must retain snapshot consistency.
- **MUST** give every mutation an operation ID, persisted data generation, and an
  expected record version where applicable. In one short transaction: validate,
  check replay, mutate all affected rows, and store a durable receipt. Identical
  operation/input replays return that receipt; conflicting input is rejected.
  Edits/deletes prove affected rows; uniqueness is database-enforced, not a Mutex.
- **MUST** use atomic workflow ports for multi-record commands; no network/file/
  notification/DataStore calls inside Room transactions.
- **MUST** distinguish `Rejected(reason)`, `Committed(receipt, followUpState)`, and
  `OutcomeUnknown(operationId)`. Rethrow cancellation; it does not prove rollback.
  Recover uncertain outcomes with the **same** ID before offering another write.
  A failed notification/export/preference update cannot invalidate saved money.
- **MUST** implement record deletion with six-second undo and durable, guarded
  compensation that cannot overwrite later edits. The elapsed timer survives
  same-process recreation without extension; process death expires the offer while
  durable deletion/compensation outcomes remain recoverable. Financial time cannot
  alter the deadline; offer expiry must not roll back an already committed compensation.
- **MUST** use archive-only category removal in MVP: retain identity/history and
  current-month limits, stop automatic budgets in later archived months, and apply
  [approved D07 restoration/visibility](docs/decisions/0001-mvp-contract.md#d07--archive-and-undo)
  with explained consequences. Any future destructive aggregate deletion needs
  separate approval, counts/confirmation and an explicit transaction, never a cascade.
- **MUST** fence reset/restore against stale commands/jobs with a persisted
  generation. Recovery receipts survive the replacement they describe. Reset
  requires counts, then a second confirmation typing **BORRAR**.
- **MUST** show data that cannot be opened at start as a failed start with retry,
  never a crash, an empty history or the device zone in its place. Starting over
  from that screen needs the typed **BORRAR** and sets the unreadable file aside
  instead of deleting it; see [ADR 0008](docs/decisions/0008-unreadable-storage-start-over.md).
- **COULD** add durable pending follow-up records only for actual consumers;
  financial receipts are required now, a generic outbox is not.

## 6. Presentation and Sello design

- **MUST** follow immutable `State / Action / Event / ViewModel / ScreenRoot /
  Screen`: stateless rendering, explicit actions, Root-owned lifecycle/navigation
  effects. Local state is ephemeral visuals only; small drafts/selections/operation
  IDs use `SavedStateHandle`, large datasets reconstruct from persisted IDs.
- **MUST** distinguish Loading, Ready (including true empty), and Failed with
  retry/optional last-good data. Durable submission results belong in state/receipts,
  not only lossy event channels. Recomposition/restoration never submits again;
  success stamps require a confirmed receipt, and edits do not celebrate creation.
- **MUST** centralize theme, paper, stamps, money, inputs, charts, and feedback.
  Features map domain models to presentation primitives for design-system APIs.
- **MUST** apply the [design philosophy](docs/design/philosophy.md): lead home with
  budget margin after upcoming-payment reservations, show the reserved amount at
  lower priority and keep overall allowance secondary. Keep essential controls visible
  and stable, make amount entry immediately available and use brief confirmed-save
  feedback. Amounts use the compact Sello pad and the note a composer docked to the
  keyboard, as designed in [Sello v2](docs/design/v2/README.md) (ADR 0013), preserving
  stable controls and accessible native input.
  Keep home's content restrained; detailed suggestions belong in Resumen. When
  Metas ships, include a compact user-chosen goal, defaulting to the active goal
  with highest completion percentage. Financial replacements require §4's approvals.
- **MUST** preserve answer-first paper, category circles, no home pace graph/shadows/
  warning cards; Cobalto/Violeta × light/dark, Sistema default, bundled Schibsted
  Grotesk/Saira Stencil One and Material Symbols Rounded. Use stamps with a clear
  purpose and at most one large budget verdict; goal milestones/completion may earn
  dedicated celebrations, respecting reduced motion. Colors/type/spacing/motion come
  from tokens, copy from resources/plurals. Keep exact overrun facts and neutral copy.
- **MUST** keep full exact amounts on slips/lists and accessible labels; abbreviate
  only chart axes. Respect reduced motion, ≥12sp text, ≥48dp hit targets, contrast,
  keyboard/Switch Access/TalkBack, and font scales 1.0/1.3/2.0 from the first slice.
- **MUST** apply [approved D06 ordering](docs/decisions/0001-mvp-contract.md#d06--ordering):
  selected-month expense count/spending/limit metrics, latest added expense rather
  than configuration/expense modification for the latest-activity option, reversible
  direction and deterministic alphabetical/ID
  ties. Unlimited categories remain last even in alphabetical/reversed orders.
  Último gasto agregado uses the newest original real creation Instant among
  selected-month nondeleted expenses. Edits/restore do not refresh it; deletion
  falls back to surviving additions. No-expense categories stay last within their
  finite/unlimited partition in either direction; original configuration-audit
  ordering is superseded. Reverse direction, not the newest-per-category metric.
- **MUST** implement saved tab stacks, shared month for Recibo/Resumen/detail,
  no future month selection, and reset month after >30 background minutes without
  clearing drafts. Dock always opens an expense; goals/investment movements start
  in their own screens. Back returns to Recibo before exiting.
- **SHOULD** use adaptive Material APIs for compact/landscape/medium/expanded
  layouts; follow the reference's breakpoints, insets, keyboard behavior, and side
  panels. Deep links only validate/prefill a draft; confirmation is required to save.

## 7. Recovery files and privacy

- **MUST** version portable backups independently of Room. Include history/order
  and portable preferences, not jobs/device state/debug/secrets. Reject unknown versions.
- **MUST** read/stage/validate before restore confirmation and atomic replacement:
  default bounds 10 MiB, 100,000 records, 10,000 characters/field, bounded nesting;
  check references, duplicates, amounts, dates, currencies, and ledger validity.
  A bad file makes no changes. Resume preference completion separately and never
  report complete restore while settings remain unfinished.
- **MUST** apply approved D09: restore replaces, never merges, after a validated
  current/replacement-count preview and explicit warning. Restore the financial zone
  and portable preferences, not system permissions. Reset clears finances and portable
  preferences to first-run defaults; retain only minimal nonfinancial fencing/recovery
  metadata and explicitly explain that external backups survive. See
  [D09](docs/decisions/0001-mvp-contract.md#d09--restore-and-reset).
- **MUST** use the document picker; “Guardado” requires successful write/close.
  Analytical CSV/JSON exports are not backups; use canonical range/revision values
  and neutralize spreadsheet formula injection.
- **MUST** keep private local storage, disable unapproved OS backup/device transfer,
  redact diagnostics, minimize permissions, and narrowly scope/clean shared cache
  files. Disclose plaintext backups/exports; checksums are not encryption. Read and
  write only provider (`content://`) documents the pickers return, never a path.
- **MUST**, for approved D08 MVP policy, use manual plaintext JSON backup without
  password/encryption claims. Explain readable financial contents before saving,
  manual-recovery responsibility, provider-managed sync for cloud-backed destinations
  and that app reset does not erase external copies. Clean private temporary files.
  The merged-manifest, release and file/log checks in
  [MVP data handling](docs/privacy/mvp-data-handling.md) enforce these; keep them
  passing rather than describing protection they do not test.
- **COULD** add reviewed encryption or an isolated legacy JSON translator after
  an explicit product/security decision. Never custom crypto or old database import.

## 8. Contracts activated by later features

- **MUST**, for recurrence: retain calendar anchors when clamping month ends;
  distinguish every 15 days from twice-monthly; preserve past occurrences when
  editing future schedules. Use unique `(scheduleId, effectiveDate, slot)` origins
  (two twice-monthly days can clamp to one date) and atomic creation/checkpoints;
  every catch-up trigger shares one use case, with no background work (ADR 0017).
  Bound/resume catch-up, expose per-item progress, and retry only transient faults.
- **MUST**, for FX: decimal decoding, positive bounded rates, source/as-of/fetched-at
  metadata, atomic mode/revision checks; late network responses cannot overwrite
  manual rates. Keep last-good rates, expose stale/missing/error states, and never
  present a partial COP subtotal as a complete portfolio. Confirm stale conversion.
  **SHOULD** start with 24-hour freshness, eight-second timeout, 64 KiB responses.
- **MUST**, for alerts: opt-in permission flow, shared budget snapshot and stable
  category/month/threshold identity. Posting failure cannot fail a financial save.
- **MUST**, for batches: define atomic versus per-item semantics before the UI;
  retain every operation ID/result. A partially committed batch is not “rolled back”.

## 9. Developer tools: simulation and catalog

### Live-testing sandbox

- **MUST** provide a debug-only tools entry point and a distinct debug application
  ID/private data store, installable beside production. Clearly label sandbox mode;
  simulated data, clock controls, fixtures, routes, and fault injectors must be
  absent from release artifacts—not merely hidden by `BuildConfig.DEBUG`.
- **MUST** inject an observable financial clock: freeze, advance by day/month,
  jump to a date, and bounded step/play/pause. Trigger the same rollover/snapshot
  recomputation as normal use. Do not change device time or accelerate monotonic
  timers, UI animation, undo expiry, network timeouts, or real audit timestamps.
- **MUST** test real feature paths: Room, domain commands, receipt recovery, and
  chart projections. Seed/replay synthetic scenarios through validated commands
  or import contracts; never alternate financial formulas or unchecked DAO edits.
- **MUST**, when recurrence ships, expose explicit “run due work” and bounded
  catch-up using the production use case, uniqueness, and checkpoints. Show
  created/skipped/failed/pending results. Virtual time does not advance Android's
  WorkManager scheduler; scheduler integration needs separate worker/device tests.
  Sandbox stepping must control competing automatic work and external side effects.
- **SHOULD** offer reproducible seeded histories and scripts for graph growth,
  month/year/leap-day boundaries, month-end anchors, missed runs, restart, duplicates,
  and failures. Persist sandbox time/zone/scenario/checkpoint for restart tests;
  rewind mutated history only by resetting/replaying an isolated scenario.
- **MUST** expose active date/zone, scenario/seed, operation receipts, revisions,
  and catch-up progress without sensitive logs. Simulation metadata stays out of
  portable user backups. Reset is sandbox-scoped, cancels/fences its work, and
  restores a known baseline. Diagnostic export is synthetic/redacted and explicit.
- **MUST** add applicable live scenarios with each new financial capability;
  manual exploration complements—not replaces—automated regression tests.
  **COULD** add scripted fault injection and accelerated long-running simulations
  behind debug-only adapters as real workflows require them.

### Component catalog

- **MUST** use the separate `:catalog` application module with its own application
  ID and launcher; no extra product flavor is needed initially. It renders the
  **actual** `:design-system` components, never copied implementations or app screens.
  It is a developer artifact, not a customer release.
- **MUST** group examples by type: foundations/tokens, paper, stamps, money/text,
  categories, inputs, navigation/layout, charts, and feedback/overlays. Use synthetic
  render fixtures and local interaction state; no repositories, permissions, or
  production DI graph. Catalog-only chrome/fixtures stay outside design-system.
- **MUST** offer all four themes, font scales, compact/landscape/expanded widths,
  and applicable empty/loading/error/busy/disabled/large-value/interactive states.
  Each new reusable component ships with examples and Compose previews.
- **SHOULD** give examples stable IDs for targeted UI/accessibility tests and
  share fixtures with previews/tests where practical. **COULD** add screenshot
  regression testing once rendering baselines are stable.

Integration references: [build variants/source sets][build-variants] and
[WorkManager testing][work-testing].

## 10. Enforcement and evolution

- **MUST** make SELLO-004's [quality flow](docs/development/quality-gates.md)
  a prerequisite for subsequent implementation: one ticket-derived gate runner,
  validated evidence bound to the verified change set before Review/Done, and
  trusted CI enforcement on the real merge result before protected merges. Passing
  automation never substitutes for acceptance review.
- **MUST** follow the repository-owned [ticket folders](docs/tickets/README.md)
  for execution/dependencies and ticket delivery evidence. They operationalize
  these guardrails; it does not override financial contracts or expand MVP scope.
- **MUST** use meaningful red→green behavior tests, controlled clocks/virtual time,
  external fakes, and real Room for transactions/migrations. No arbitrary sleeps,
  live network, global DI, or test-order assumptions.
- **MUST** cover duplicates, cancellation around commit, stale versions, missing
  rows, overflow rollback, read-error-not-empty, guarded undo, malformed backup,
  restore preference failure, and reset fencing before MVP release. Later gates
  include recurrence restart, withdrawal-not-loss, FX races, and milestone history.
- **MUST** gate code changes with architecture/static checks, unit tests, lint, and
  debug/release assembly; affected device flows and migration tests run in CI. The
  required local run is that host gate; the hosted check runs the device suites
  (ADR 0014, amended 2026-10-24).
  [ADR 0014](docs/decisions/0014-change-aware-quality-gates.md) permits Python/Git-only
  G0 for documentation advancing no ticket and explicit dependency-aware G2 device
  scope. Shared/build/unknown inputs require full device coverage; nightly/manual
  runs remain full. Reports must distinguish targeted execution from full G2.
  Completion evidence is bound to the pull request's own change set, and the hosted
  run on the merge result, for pull requests and merge queue entries alike, verifies
  the combination (ADR 0014, amended 2026-10-24).
  Release additionally requires release-composition checks, dependency review,
  accessibility/adaptive smoke tests, shrinking, and production signing validation.
  Assembly is not device execution; ephemeral CI signing is not customer-distribution signing.
- **MUST** also assemble the catalog, exercise sandbox restart/rollover scenarios,
  and verify release artifacts contain no debug tooling, fixtures, or test clocks.
- **SHOULD** keep previews for four themes and loading/empty/error/busy/large-money
  states. Measure startup/large-list responsiveness before adding optimizations.
- **MUST** record significant deviations in short ADRs (context, alternatives,
  decision, consequences, verification); update changed guardrails here.
- **MUST** settle MVP scope acceptance, supported API/toolchain matrix, backup
  plaintext policy, and forecast examples before the first release. Legacy import
  and investment/rate semantics are explicit later decisions—not hidden defaults.

## References and precedence

Product/design direction: [Sello design philosophy](docs/design/philosophy.md),
recorded 2026-10-15, with the explicit supersessions and transition in
[ADR 0011](docs/decisions/0011-goal-centered-design.md). The visual authority is
[Sello v2](docs/design/v2/README.md) ([ADR 0013](docs/decisions/0013-sello-v2-design.md)),
with the [screen/interaction briefs](docs/design/screen-briefs/README.md) holding the
intent and open policy behind it. The unchanged **Sello v1.1,
2026-10-08** snapshot, excluding widgets, remains the visual authority for tokens,
components and layouts v2 does not show. Legacy routes, migration hints, sample figures and
Kotlin sketches are not a new-app schema or financial oracle. This document's
explicit scope/financial/safety rules override those sketches; open replacement
financial definitions need approval before implementation, as described in §4.

- Visual reference: [Sello v2](docs/design/v2/README.md) and its [component catalog](docs/design/v2/components.html)
- v1.1 source (provenance for the snapshot): `/home/okabe94/.t3/worktrees/FinanceTracker/t3code-5c84db69/docs/design/ui-overhaul/sello-spec.html`
- Historical lessons: `/home/okabe94/AndroidStudioProjects/FinanceTracker/docs/greenfield/PROJECT_LESSONS.md`
- Prior proposal: `/home/okabe94/AndroidStudioProjects/FinanceTracker/docs/greenfield/ARCHITECTURE_SPEC.md`

These external paths are provenance, not build dependencies. The local
[design snapshot](docs/design/sello-spec.html) and [provenance](docs/design/README.md)
are the executor references; no task requires the original worktree. Scope/financial
approvals remain explicit SELLO-001 deliverables, not implied by copying the reference.

[android-architecture]: https://developer.android.com/topic/architecture/recommendations
[navigation]: https://developer.android.com/guide/navigation/design/type-safety
[room]: https://developer.android.com/training/data-storage/room
[datastore]: https://developer.android.com/topic/libraries/architecture/datastore
[koin-workers]: https://insert-koin.io/docs/reference/koin-android/workmanager/
[agp-kotlin]: https://developer.android.com/build/migrate-to-built-in-kotlin
[build-variants]: https://developer.android.com/build/build-variants
[work-testing]: https://developer.android.com/develop/background-work/background-tasks/testing/persistent
