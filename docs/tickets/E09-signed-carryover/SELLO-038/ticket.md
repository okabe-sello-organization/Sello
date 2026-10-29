# SELLO-038 — Implement exact signed carryover domain calculations

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-037, SELLO-014, SELLO-004
- **Gate:** G1

### Outcome
Provide one exact, deterministic financial calculation consumed by all later paths;
UI/storage must not independently reinvent rollover or forget an earlier deficit.

### Deliverables
Domain-owned signed-carry calculation and typed policy/provenance contracts integrated
with the actual monthly snapshot API. Publish boundary documentation and focused
`SignedCarryoverPolicyTest`/`CarryoverCorrectionReplayTest` (target names).

### Acceptance criteria
- Approved multi-month calculations distinguish base/carry/effective capacity/remaining,
  preserve signed values and use checked/widened arithmetic, never floating-point truth.
- Replay follows effective month ordering and approved transitions; corrected history
  has a deterministic affected range. No financial wall-clock reads or storage dependency.
- Missing history, invalid mode inputs and unrepresentable totals produce typed failures,
  not zero carry. No-carry mode preserves all approved MVP snapshot/forecast examples.
- Income/net cash flow remain separate from carry; forecast/verdict/allowance reuse
  the approved policy, including COP 50 allowance rounding without erasing deficits.

### Tests
Meaningful red→green unit tests with SELLO-037's hardcoded reviewed values; cover
multi-month signed chains, corrections, skipped months, transitions, zero/negative
capacity and Long intermediate/result overflow. Run existing monthly-policy tests and G1.

### Working checkpoint
Pure calculations and existing released app/catalog compile and pass; production
customers cannot activate incomplete storage/UI. No successful placeholder ports.

### Context and starting points
Inspect actual `:domain` money/month snapshot and port conventions from SELLO-010/014,
not names guessed from this plan. The SELLO-037 policy and example corpus are required
inputs; read architecture §§2/4 and ADR 0002.

### Implementation plan
1. Map approved outputs and failure states onto existing exact money/snapshot contracts.
2. Add red tests for reviewed chains and invalid inputs before changing calculations.
3. Implement deterministic replay and provenance/affected-range results off UI logic;
   widen arithmetic and validate each representable output.
4. Integrate snapshot/forecast/allowance policy without rewriting unrelated domain APIs;
   preserve original no-carry behavior and cancellation ownership conventions.
5. Publish consumer-shaped storage/read needs and run focused suites then ticket G1;
   retain evidence and request designated review.

### Concrete cases and pitfalls
- Clamping a negative spending allowance is presentation/planning behavior, not
  permission to clamp the underlying deficit or fabricate positive capacity.
- Effective capacity cannot be computed from only previous base minus spending if
  an earlier carry already influenced that month's capacity.
- Test expectations must come from the reviewed corpus, not another invocation of
  the production calculator. Unknown history is not identical to genuine zero spending.

### Verification recipe
Run new exact-domain tests and legacy `MonthlyBudgetPolicyTest`, then the ticket
runner's G1. Record reviewed input IDs, boundary results and original MVP regressions.
