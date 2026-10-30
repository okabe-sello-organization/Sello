# SELLO-042 — Extend live sandbox and regression corpus for signed carryover

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-041, SELLO-024, SELLO-025, SELLO-027, SELLO-004
- **Gate:** G2

### Outcome
Developers can reproduce accumulating surplus/deficit, month progression and late
corrections through real financial commands instead of fabricated chart data.

### Deliverables
Named synthetic carryover scenarios, resumable deterministic command scripts,
reviewed expected snapshot/graph checkpoints and sandbox runner documentation.
Extend the existing developer tooling rather than creating a second simulation engine.

### Acceptance criteria
- Financial stepping crosses consecutive/skipped/short/leap-year months; actual
  storage/domain paths produce carry, graph and receipt values matching reviewed inputs.
- Cover surplus, repeated overspend, deficit beyond next base, correction cascades,
  opt-in transitions and restore/reset interruption using approved policy.
- Repeated/resumed scenarios do not duplicate financial records or reset prior carry.
  Financial stepping does not alter audit timestamps or monotonic undo deadlines.
- Scenario data remains synthetic/isolated, bounded and absent from customer builds;
  customer histories are never manipulated by sandbox commands.

### Tests
JVM command/replay/checkpoint suites through the real commands before/after restart,
interruption, restoration and reset; no new device tests (see the epic's Decisions). Assert independently reviewed
exact snapshot/graph values; no arbitrary sleeps or fabricated financial success; G2.

### Working checkpoint
An independent developer can replay a named multi-month carryover history and inspect
the same availability customers see, while customer release remains developer-tool-free.

### Context and starting points
Inspect the actual sandbox protocol/fixtures from SELLO-024/025/027 and current
carryover ports/UI. Read architecture §9, SELLO-037 examples and ADR 0002; financial,
audit and monotonic clocks remain distinct.

### Implementation plan
1. Define stable scenario IDs and reviewed input/expected-checkpoint records; reuse
   existing synthetic fixtures and operation-ID/resume conventions.
2. Add red tests for representative signed chains and interrupted replay without duplicates.
3. Implement scenario scripts using real settings/expense/correction/restore commands;
   step only the injected financial clock.
4. Expose existing sandbox progress/read failures and exact graph/snapshot provenance;
   document reset/replay and isolate scenario storage.
5. Execute replay/reopen/restore/reset cases on the JVM and the release-artifact
   isolation check; run G2 and publish reusable regression/runbook evidence.

### Concrete cases and pitfalls
- A scenario that writes precomputed carry/graph arrays cannot prove production replay.
- A repeated late correction must recover the same receipt, not cascade twice.
- Month stepping alone cannot serve as proof of real audit/undo timing correctness.

### Verification recipe
Run carryover scenario regression tests and G2. Retain
scenario IDs, reviewed expected values, real receipt/reopen checkpoints and release checks.
