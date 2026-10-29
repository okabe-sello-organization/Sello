# SELLO-037 — Approve signed carryover policy and independent examples

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-036, SELLO-004
- **Gate:** G1

### Outcome
Turn the approved honesty principle into an implementable contract before enabling
carryover. Both surplus and deficits must survive; rollout/opt-in semantics are not
implied by the direction ADR.

### Deliverables
An approved `docs/decisions/0003-signed-carryover-policy.md`, versioned
`docs/testing/signed-carryover-examples.md`, acceptance-to-ticket mapping and reviewed
presentation wording for base limit, carry-in, effective capacity and remaining.
Record product/independent technical approvers and exact approved answers.

### Acceptance criteria
- Define per-category versus global scope, default/opt-in, effective start month,
  starting carry, missing/skipped history and continuous multi-month formula.
- Define enable/disable/re-enable, archive/unarchive, finite/unlimited/zero transitions,
  base-limit edits, caps, negative effective capacity and out-of-range handling.
  No deficit can be silently forgiven or clipped; any settlement requires explicit policy.
- Define historical correction cascades, preview/confirmation scope, report provenance,
  forecast/verdict denominator and income-allocation treatment without counting carry as income.
- Independently approve at least three consecutive months, surplus, deficit, deficit
  larger than next base, skipped months and correction cases. No unresolved financial
  choice is left for the domain/data/UI executor to invent.

### Tests
Independently check integer/rational arithmetic and policy transitions; validate
document links and board outputs, then G1. Documentation work needs no invented
behavioral red test. Missing approvals prevent Done.

### Working checkpoint
Existing released MVP remains unchanged/buildable; each successor can use concrete
approved inputs rather than an ambiguous rollover description.

### Context and starting points
Read ADR 0002 and accepted D01/D03/D04 portions in
[MVP decisions](../../decisions/0001-mvp-contract.md). Inspect the released
monthly snapshot, command and backup contracts from SELLO-013/014/028–031; the
ADR's C01/C02 illustrate intent, not a complete approved replay algorithm.

### Implementation plan
1. Inventory actual released contracts, source ownership and retention guarantees;
   list decisions that change existing financial/report/portable meanings.
2. Present concrete alternatives to the product owner; record selected policies,
   reasons, rejected alternatives and effective-date rules in the new decision record.
3. Work multi-month examples with independently calculated expected values. Separate
   base limit, carry, effective capacity, spending, remaining and income allocation.
4. Obtain product approval and independent financial/technical example review;
   update guardrails through an ADR for any accepted architecture change.
5. Map approved examples/edge cases to SELLO-038–043 and publish the contract;
   run documentation checks and the ticket runner, record Quality run and request review.

### Concrete cases and pitfalls
- Initial base 100,000, spend 120,000 gives −20,000 carry and next capacity 80,000
  under the ADR's conditional assumptions; surplus-only rollover is not this feature.
- In the continuous candidate, spending 70,000 from capacity 80,000 leaves 10,000
  and next capacity 110,000, not 130,000. Review rather than assume this formula.
- A past expense correction may alter later derived availability while configured
  later base limits stay unchanged; users need a clear impact explanation.

### Verification recipe
Use SELLO-004's actual ticket runner with G1, plus link/board checks. Attach signed
decision/example review and requirement mapping; no approval claim from agent self-review.
