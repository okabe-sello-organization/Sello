# SELLO-014 — Calculate consistent Recibo and Resumen budget snapshots

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-012, SELLO-013, SELLO-004
- **Gate:** G2

### Outcome
Define shared actuals/projections once so slips, details and charts cannot disagree.

### Deliverables
Snapshot read port at one revision/as-of/zone, exact category/month aggregates,
remaining/allowance/projected spending/verdict policies and explicit read states.
Provide approved cutoffs/rounding from SELLO-001; MVP recurringDue is zero because
recurrence is absent, not because a failed schedule read is discarded.

### Acceptance criteria
- Unlimited spending still affects totals; no limits yields Sin límite. Past months
  use historical limits/actuals and omit allowance. Today counts as an available day.
- Approved B08: a category deficit does not zero still-positive overall allowance.
  On Oct 22, three limits of 100,000 with one category spending 110,000 yield
  overall remaining 190,000 and daily allowance 19,000; preserve category deficit
  −10,000/Pasado without silently reallocating limits. Test category/global scope separately.
- Actual over-limit always wins; projected ≤95%, >95–100%, and >100% boundaries
  match examples. Exact source amounts remain available to chart render models.
- Read failures retain explicit failed/last-good states, not financial zero.
  Future entries are excluded; concurrent writes cannot produce mixed snapshots.

### Tests
Worked-example domain tests for first/last day, short months, 95/100% edges,
negative remaining, no limits, large sums; real-Room snapshot consistency/read faults.

### Working checkpoint
Consumer ports provide truthful current/historical totals to the coming slice.
G2 passes; no screen duplicates financial formulas.

### Context and starting points
Home/detail/summary need one shared policy, not separately combined DAO flows.
Inputs are actual command/schema contracts, approved D03/D04 and the independent
examples. Read [snapshot checklist](../../development/execution-guide.md#contract-and-failure-checklist)
and architecture §4; forecasts cannot be copied from reference drawings.

### Implementation plan
1. Define a monthly snapshot carrying generation/revision/as-of/zone, exact category
   actuals/limits and totals, plus explicit loading/failed/last-good read semantics.
2. Read related financial rows at one consistent Room revision; perform exact bounded
   aggregation off main. Recompute on real/injected date changes and committed revisions.
3. Implement approved remaining/allowance/pace/projection/verdict policy and past/no-limit
   branches; future rows and recurringDue rules must be explicit, not hidden filters.
4. Return exact numeric/provenance values, not formatted Spanish labels or Float chart
   series. Keep forecast distinct from actuals and mark unavailable totals as failures.
5. Implement tests before screen wiring; publish the read/policy contract for Recibo,
   previews, Resumen and eventual alerts without constructing those later adapters.

### Concrete cases and pitfalls
- B01–B04/V01–V04/H01 must pass. A negative remaining allowance is zero with loss
  semantics; no-budget is not a numeric-zero budget. On Oct 31 allowance includes today.
- Approved D03 rounds only the displayed daily planning allowance down to COP 50
  multiples: 3,529 → 3,500; 3,564 → 3,550. Cover A01–A08 from the versioned corpus,
  including exact 50/100 boundaries and positive sub-50 allowance without loss ink.
  Preserve transaction/remaining-budget precision and exact forecast verdict comparisons.
- Concurrent expense and limit edit cannot show old budget with new spend in one
  snapshot. Last-good data from another month/date must be labeled with its source.
- Unrepresentable report totals fail explicitly; SQL SUM promotion or Float conversion
  cannot rescue them by inventing a different amount.
- Keep configured limit, recorded spending and derived allowance distinct in the
  domain snapshot. Future signed carryover has a separate base/carry/capacity meaning
  (ADR 0002); do not hard-code discarded history or add dormant carryover modes.

### Verification recipe
Create/run `./gradlew :domain:test --tests '*MonthlyBudgetPolicyTest'` and real-Room
`MonthlySnapshotConsistencyTest`, then G2. Include approved day-cutoff/rational-rounding
examples, first/subsequent read faults and observable clock-triggered recomputation.

### Execution progress
2026-10-10: started on branch `sello-014-budget-snapshots` after SELLO-013 merged. No
product decision or dependency was needed and the schema did not change.

Delivered. `:domain`: `MonthlySnapshot` and its parts, `MonthlyBudgetPolicy` with the
approved D03 rules, and the `MonthlySnapshots` port with `Loading`, `Ready` and
`Failed` states. `:data`: `RoomMonthlySnapshots`, which reads a month in one
transaction and observes committed changes and the financial day. `:app`: the port
bound in the graph; no screen uses it yet. `docs/development/monthly-snapshot.md`
states the contract for the screens.

Choices made during the work, for owner review:
- The snapshot covers spending and budgets only. Income, net flow and "por asignar"
  join it with the income tickets (SELLO-021, SELLO-022), which is where the plan
  puts them; so do per-day series for charts.
- Each category with a finite limit gets its own remaining and verdict by the same
  rule as the whole month, as B08 requires. Only the whole month has a daily allowance.
- An archived category is listed in a month only if it has a limit or spending
  there. A category that is not archived is always listed, even in months before it
  was configured.
- A finished month is "over" only when more than the limit was spent; spending
  exactly the limit is within it.
- A month after the current one is refused instead of shown empty.
- Expenses dated after the financial day are left out and their count is reported,
  so a clock moved back cannot silently hide money.
- Sums are done in the app with exact arithmetic over rows read in pages. SQL does
  not add anything.
- When the latest read fails, the last good snapshot of that month is kept alongside
  the failure, with its own date and revision, for the screen to show as older data.

Found on the way: the first device run failed every test because the policy used a
Java constant that Android 11 does not have. Host tests cannot see that; the device
tests did, and the constant was replaced.

Left for later: the screens that show this (SELLO-017, SELLO-018, SELLO-022); income
figures and chart series; the sandbox scenarios that step through days (SELLO-024).

This is the last ticket of the epic: with it, all five are proposed Done and so is
the epic.
