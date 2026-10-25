# SELLO-105 — Verify what exists for Inversiones and what is missing

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G1

### Outcome
An exact, current list of what the app, the design system and the catalog already have for Inversiones, against the approved design, so the following tickets build only what is missing.

### Deliverables
A gap list in `docs/design/v2/gaps/` for Inversiones: each designed element marked present (with its code and tests), partial (what differs) or missing; the following tickets updated, split or closed to match, with owner approval for any scope change.

### Acceptance criteria
- Every element of the v2 Inversiones page is checked against the running app and the code, not only the docs.
- Each present element names its code path and test; each partial one says exactly what differs.
- Work done outside these tickets (other branches, merged PRs, catalog components) is found and credited.
- SELLO-106 to SELLO-112 are revised to the list before any starts; changed scope is recorded and owner-approved.

### Tests
The audit itself, with screenshots of the running app where it differs from the design; G1.

### Working checkpoint
The gap list is merged and every later ticket in this epic matches it.

### Context and starting points
The screens have been worked on outside these tickets (owner, 2026-10-23); [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md) records the decisions. Design: [v2 README](../../../design/v2/README.md) and `docs/design/v2/components.html` (Exists / Extend / New).

### Implementation plan
1. List every designed element, state and rule.
2. Check each in the app on the emulator and in the code and catalog.
3. Write the gap list and revise the following tickets; ask the owner about scope changes.

### Concrete cases and pitfalls
- Do not trust a document's claim that something exists: open the screen and the code.
- Do not start building while auditing; findings go into the list and tickets.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-105 --retain`.

### Execution progress

- **Audit, 2026-10-25:** [gap list](../../../design/v2/gaps/inversiones.md) at `main` `c8d0b4c`,
  checked in the code, the app and the catalog on an API-30 emulator
  ([screenshots](artifacts)). The drawing mostly exists from the v2 component pass
  (`PerformanceLine`, `InvestmentRow`, `RecordedValuesChart`, `SplitBar`, the ink dock);
  there is no investment in the domain, storage, backup, tab or sandbox, and no other
  branch or pull request holds investment work. USD has no design.
- **Tickets revised, owner-approved 2026-10-25:** the owner answered the open rules and
  delegated the USD design lock; SELLO-106 also designs what the page lacks, SELLO-107,
  SELLO-109, SELLO-110, SELLO-111 and SELLO-112 name the parts they reuse and extend, and
  SELLO-110 now follows SELLO-111 because a USD investment asks for the rate first.
