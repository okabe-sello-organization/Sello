# SELLO-022 — Deliver basic Resumen with exact actuals and history charts

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-014, SELLO-018, SELLO-021, SELLO-009, SELLO-004
- **Gate:** G2

### Outcome
Users can inspect actual income/spending and understand planning allocation without account-balance claims.

### Deliverables
Resumen render models/screens/Root, actual monthly spending/income/net cash flow,
budget destination/undestined explanation, basic exact spending-history/category
charts and source history links. Enable the second real tab with restored stacks.

### Acceptance criteria
- Figures reconcile to same-revision expense/income history; net cash flow and
  undestined planning capacity are distinct. No goals/investment contributions are
  fabricated; actual verdict is labeled separately from Recibo's projected verdict.
- Approved presentation: emphasize Ingresos menos gastos in Resumen; keep Restante
  del presupuesto/daily allowance prominent in Recibo and Por asignar al presupuesto
  as secondary planning (replaces Sin destinar). Explain income-minus-expenses versus
  income-minus-limits without generic Disponible/account-balance claims. Preserve
  negative allocation; expense-only corrections leave allocation unchanged.
- Shared month and tab stacks survive switches/restoration. Empty history produces
  truthful empty states, not invented comparison trends or zero on read failure.
- Chart selection/text values match exact source totals even at large amounts.
  No analytical export, detailed record/comparison or unsupported navigation exists.

### Tests
Approved allocation/actual verdict examples, chart/source agreement, tab/month/back
restoration and device entry→summary→restart journeys; all four themes and G2.

### Working checkpoint
Recibo and Resumen are both fully functional product tabs; each link points to an
implemented workflow. Sandbox can now demonstrate real graph growth.

### Context and starting points
Resumen explains actual cash flow and planning allocation; neither is a bank balance.
Use monthly snapshot contracts from SELLO-014, income history from SELLO-021 and
chart primitives. Read [Resumen](../../design/sello-spec.html#s-resumen), approved
scope/examples I01/G01, and architecture §4. Detailed comparisons/records/export are deferred.

### Implementation plan
1. Extend/read a same-revision snapshot containing actual expense/income totals and
   approved budget allocation; define net flow and undestined as distinct render values.
2. Build Resumen State/ViewModel/Root/Screen with actual verdict, monthly history/
   category chart inputs and source-income/category links to implemented routes.
3. Preserve exact series values/IDs through selection and accessible text. Projection
   labels stay distinct from the actual-spend verdict and never create financial records.
4. Enable the real second tab, retained stacks/month/back/reselection; remove temporary
   one-tab scaffolding without adding Patrimonio or broken future action links.
5. Implement loading/empty/error/last-good and insufficient-history states; add previews
   and any newly required reusable component example states.

### Concrete cases and pitfalls
- I01 shows net cash flow 120,000 and undestined 50,000, not two copies of the same
  number. No goal/investment amount exists in MVP to fill allocation fields.
- G02's corrected expense changes graph total 30,000→27,000, remaining 70,000→73,000
  and net flow 170,000→173,000; Por asignar al presupuesto stays 100,000. Test numeric
  independence, accessible labels and screen hierarchy; updated labels do not change formulas.
- G01 cumulative chart is 10,000/25,000/30,000; selected values/text equal source
  rows even when geometry uses approximate coordinates.
- Resumen and its charts are further consumers of the same month's snapshot, which
  each screen observes separately today. Sharing one observation was parked in
  SELLO-055 (2026-10-12) as not worth its risk for two screens; decide it again here,
  with measurements, before adding more.
- Month switches cannot combine October income with September budgets; no-history
  cannot produce an invented comparison trend or a zero on failed reads.

### Verification recipe
Create/run `*ResumenSnapshotTest`, `*ResumenRenderModelTest` and ResumenJourneyTest;
G2. Compare independently totaled Room rows with slips/chart/selected text, test
tab/month restoration and read faults, and record every exposed link's real destination.

### Execution progress
2026-10-13: started from merged SELLO-021 on `main` at `71d1a32`, on branch
`sello-022-resumen`. The owner approved two PRs: consistent data/calculations first,
then screens/charts. SELLO-022 stays In Progress until the second is accepted.

The first PR adds a consumer-shaped Resumen snapshot without changing Recibo's
existing snapshot contract. Selected-month budgets, income, expenses and up to twelve
months of spending history are read in one transaction, then calculated in domain.
Current/future/past cutoffs, original category identities, exact daily/cumulative
amounts and historical limits remain explicit. No schema or dependency change is
planned. Snapshot observation cost will be measured before adding UI consumers;
sharing observations is not assumed necessary and SELLO-055 is not silently folded in.

Necessary read-boundary adjustment: the closed-database test exposed Room reporting
the base Android `SQLException`, not only `SQLiteException`. The shared read helper
will recognize that database exception as a typed unavailable read, while preserving
cancellation and leaving command/unknown-write handling unchanged. Both Resumen and
the existing income read port are covered by the regression test.

Part 1 implementation now supplies exact income/net flow/allocation, actual budget
verdict, daily/cumulative values and bounded monthly history with historical limits.
The live UI is unchanged. Domain tests prove I01/G01/G02 and cutoff/overflow cases;
real-Room tests cover concurrent commits, malformed rows, reopen, paging, cancellation
and computation outside the transaction. Two active observers measured two initial
calculations plus two after one commit; this is work-count evidence, not a latency
benchmark. The screens PR will observe once for all its slips/charts and bind that
observation to its lifecycle; no cross-port cache or SELLO-055 work is included.
Actual Room closure cancels an observation's scope: that cancellation must propagate,
while direct closed-storage read errors remain typed. The focused check distinguishes
the two rather than treating cancellation as a successful zero or a retryable read.

**First PR verification — partial ticket, not final acceptance**

- Tested clean commit `857815780231b0ea7b68f9cc52a5c5f576b4f071`, based on merged `main` at `71d1a32`. Only this verification text, the retained report and regenerated board views follow the tested revision; no code changes follow it. SELLO-022 remains In Progress; all UI/accessible selection/navigation acceptance belongs to the second PR.
- Meaningful red→green: `ResumenSnapshotTest` ran nine tests against the failing policy stub, with eight intended failures; all nine now pass. The nine initial real-Room contracts failed against the missing reader (including seven value/failure assertions; missing observation/computation work also timed out). After implementation, a real closed-storage `SQLException` still escaped; its regression passed after broadening the shared read helper's database-exception handling. Fourteen final Room contracts pass, including two-observer work counts, cancellation, first-read failures and 501 expenses plus 501 incomes beyond the storage page.
- Focused green: `./gradlew :data:ktlintCheck :domain:ktlintCheck :domain:test :data:testDebugUnitTest :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.software.sello.data.ResumenSnapshotContractTest`. The first style invocation raced formatting/check tasks, and a subsequent new long assertion was wrapped; no style rule was disabled. An experimental closed-observer assertion wrongly expected a retryable failure when Room had canceled its own scope; the final test requires propagated cancellation, preserving the architecture contract.
- Integrated `./scripts/verify-ticket SELLO-022 --retain` passed all six checks: board validation/tests, quality-runner tests, architecture, host and API-30 device gates. Host: 474 tests; device: 474 tests; zero failures, errors or skipped tests. App debug/release and catalog debug assemble; lint has zero errors and 29 warnings. Focused compilation also reports Kotlin/JDK Unsafe deprecation warnings; no toolchain, schema, permissions or dependency changes were made.
- Part 1 quality run: run `20261013T202758Z-8cf82d18`; SELLO-022 G2 passed; HEAD `8578157`; inputs sha256 `e809b7cceed3ca73a4e838da5d7d84c3f11dd5278628dbec42119ea0b5574858`; report `docs/planning/mvp/quality-reports/SELLO-022.json`, sha256 `e63f59353e7df2374f37fa1674b6749c93fe07a30d7067cc5c92e4352c6f4eed`. The final screens PR must replace this with a fresh run for its complete inputs before proposing Done.
- Device: isolated synthetic API-30 AVD `sello021_api30`, serial `emulator-5580`, separate adb server 5038, 640×1280 pixels at standard 320 dpi. No personal phone/history was used. Executed tests prove database reopen and read/clock/dispatcher contracts; no new UI, manual process-death journey, TalkBack session or four-theme Resumen screen coverage is claimed here.
- Self-review: reviewed exact sums/cutoffs, actual-versus-projected standing, signed allocation, historical budget identity, transaction/revision consistency, injected computation, cancellation, typed read failures, module boundaries and the unchanged existing UI. Independent review/hosted CI are not claimed; the owner reviews the first PR's foundation, and the second PR carries final ticket acceptance. The runbook is `docs/testing/SELLO-022-resumen.md`.

**Second pull request: complete Resumen screens**

Part 1 merged through #44. Branch `sello-022-resumen-screens` now includes current
`main`; its pull request contains only the remaining screens and charts work.
The screens use one lifecycle-bound Resumen observation, emphasize actual cash
flow, keep signed allocation secondary, and label actual standing separately from
estimated projection. Exact chart selections expire after three seconds and are
restored by source ID, never chart coordinates. Category source links reuse the
existing detail/history screen through a restored identifier-only route. Daily,
monthly and category charts reuse catalog primitives; no comparison, export,
recurrence or Patrimonio destination is added. Final tests and the retained gate are
recorded below; Done is proposed by the screens pull request.

The completed local screens input also fences the optional income preview by the
income month's exact revision. A Resumen read failure, including one with last-good
data, leaves the existing income command eligible and a regression test submits it
through to the stored receipt. The actual-spend verdict now uses the shared large
stamp, and short phone-width windows keep one scrollable column; two panes start at
600 dp. The real journey selects daily and monthly chart source IDs and compares
their displayed 30,000 COP values with independently queried Room totals.
