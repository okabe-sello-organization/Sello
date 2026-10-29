# SELLO-025 — Implement seeded histories, replay and live graph-growth scenarios

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-017, SELLO-018, SELLO-022, SELLO-024, SELLO-004
- **Gate:** G2

### Outcome
A developer can watch actual graphs/budgets grow by replaying deterministic financial actions.

### Deliverables
Versioned synthetic scenario fixtures with seed/time/zone, validated category/
expense/income commands, bounded date-step runner, pause/resume/checkpoint and
reset-to-baseline. Tools show operation IDs, receipts, revisions and step outcomes.

### Acceptance criteria
- Same baseline/seed/actions reproduces exact totals/history; each step writes through
  production commands and reads actual Room/snapshots. No unchecked DAO seed shortcuts.
- Replaying/restarting a partially completed step uses the same operation identity;
  no duplicate expenses/income. Manual pause has observable progress and no unbounded loop.
- Scenarios demonstrate day/month/year changes, no-budget/over-budget states and
  monthly graph growth. Missing recurrence is stated; no pretend scheduled entries.
- Reset is sandbox-only and restores time/fixtures; no personal data or debug
  simulation metadata leaks into normal user backups.

### Tests
Deterministic fixture/replay and partial-step restart tests; real-Room counts and
graph/source agreement; device step/play/pause/reset journey. Run G2.

### Working checkpoint
Developer can reproduce a historical chart with known exact totals and observe it
update in actual Recibo/Resumen; ordinary manual workflows still work.

### Context and starting points
A seeded graph is useful only when it exercises the same writes/calculations as
manual use. Inputs are real expense/income/summary ports and the isolated session
from SELLO-024. Read [G01](../../testing/mvp-financial-examples.md#history-income-and-graph-reconciliation--d04d06), architecture
§9 and recovery-case checklist; no recurrence port exists in MVP.

### Implementation plan
1. Define a small versioned synthetic scenario format with seed, zone, starting
   date and explicit advance/create-category/set-limit/expense/income steps.
2. Implement a bounded runner calling actual domain commands. Persist pending step's
   operation ID before writing; checkpoint after resolving its receipt. Stop on unknown outcome.
3. Add play/pause/single-step/reset/replay controls and inspectable step/receipt/revision
   results. Pause/cancel must leave resumable pending work rather than create a fresh ID.
4. Implement G01 and month-boundary/over-budget scenarios, injecting dates so entry
   validation remains real; observe actual Recibo/Resumen charts, not a separate debug graph.
5. Reset only the sandbox baseline and resume after process death through original
   step IDs. Keep scenario metadata synthetic/debug-only and outside portable backups.

### Concrete cases and pitfalls
- G01 yields three expenses, one income, cumulative 10,000/25,000/30,000 and final
  remaining 70,000. Resume after step 2 cannot add a fourth expense.
- A financial commit followed by checkpoint failure recovers its receipt; it does
  not rerun the amount under a new operation. Random seed alone is insufficient if IDs vary on resume.
- Jumping dates does not advance WorkManager or generate pretend recurring values.
  Future recurrence uses its actual product use case when implemented later.

### Verification recipe
Create/run `*ScenarioReplayTest` and real-Room ScenarioGraphGrowthTest; G2. Compare
fresh replay versus interrupted/resumed rows, receipts and exact chart labels; demonstrate
live pause/step/restart/reset on debug app with recorded scenario/version/seed.
