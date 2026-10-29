# SELLO-027 — Establish the growing temporal regression scenario suite

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-020, SELLO-021, SELLO-023, SELLO-026, SELLO-004
- **Gate:** G2

### Outcome
Turn live explorations into repeatable acceptance evidence and an extension contract for future features.

### Deliverables
Scenario inventory with IDs, seed/actions/expected totals, automation hooks and
documented replay instructions. Cover month-end/leap year, archived category,
past-budget retention, income allocation, graph growth, restart and monotonic
undo under financial time stepping. Extend it later in feature tickets.

### Acceptance criteria
- Automated tests and live runs use equivalent fixtures/contracts; expected values
  are independently reviewed examples, not copied from the implementation's output.
- Scenario resume is deterministic and diagnostic reports identify failures/revisions.
  Live testing supplements unit/device tests rather than replacing them.
- Future recurrence contract explicitly requires production run-due/catch-up use case,
  created/skipped/failed/pending results and separate WorkManager tests. No scheduling
  library/engine enters MVP merely to display a debug preview.

### Tests
Execute the complete MVP scenario corpus, compare outputs after fresh versus
resumed runs, and inspect release exclusion again; G2.

### Working checkpoint
Another contributor can reproduce each recorded temporal issue from its scenario ID
without personal data or undocumented local settings.

### Context and starting points
Live exploration becomes durable value only when another actor can reproduce its
inputs and expected outcomes. Inputs are the actual runner/fault contracts and all
MVP financial workflows. Read the example corpus, architecture §9 and guide's
[delivery record](../../development/execution-guide.md#delivery-record).

### Implementation plan
1. Create `docs/testing/sandbox-scenarios.md` listing stable ID/version/seed, setup,
   action sequence, expected exact totals/state, replay command/UI route and test ownership.
2. Implement deterministic month/year/leap-day, missing-run-date jumps, archived
   category, historical-limit, income allocation, graph-growth and recovery scenarios.
3. Reuse fixtures/actions in automated tests without deriving expected amounts from
   the production policy; independently verify the numbers and checkpoint semantics.
4. Document supported scenario extension process and future recurrence obligation:
   actual run-due/catch-up port, dedup/checkpoints/results and separate scheduler tests.
5. Execute fresh/resumed corpus, record artifacts and inspect release isolation.
   Do not add unused recurrence/work/rate dependencies to anticipate the next increment.

### Concrete cases and pitfalls
- Same ID/seed after reset produces identical financial contents; resume mid-run
  preserves logical step identities and creates no duplicates.
- Advancing financial time while an undo offer is open cannot change its monotonic
  six-second duration. Replaying across month changes retains historical limits.
- “Missed dates” in MVP is timeline/history testing, not proof recurrence works;
  document the distinction so future actors do not mark scheduling acceptance complete.

### Verification recipe
Create/run the named TemporalScenarioCorpusTest and scenario host tests, then G2.
Follow published replay instructions from a fresh debug sandbox and from saved
checkpoint. Evidence lists every scenario result and expected/actual values, not one green summary.
