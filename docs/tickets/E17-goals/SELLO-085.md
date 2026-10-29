# SELLO-085 — Verify what exists for Metas and what is missing

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G1

### Outcome
An exact, current list of what the app, the design system and the catalog already have for Metas, against the approved design, so the following tickets build only what is missing.

### Deliverables
A gap list in `docs/design/v2/gaps/` for Metas: each designed element marked present (with its code and tests), partial (what differs) or missing; the following tickets updated, split or closed to match, with owner approval for any scope change.

### Acceptance criteria
- Every element of the v2 Metas, goal detail, Aportar/Retirar, create and celebration pages is checked against the running app and the code, not only the docs.
- Each present element names its code path and test; each partial one says exactly what differs.
- Work done outside these tickets (other branches, merged PRs, catalog components) is found and credited.
- SELLO-086 to SELLO-094 are revised to the list before any starts; changed scope is recorded and owner-approved.

### Tests
The audit itself, with screenshots of the running app where it differs from the design; G1.

### Working checkpoint
The gap list is merged and every later ticket in this epic matches it.

### Context and starting points
The screens have been worked on outside these tickets (owner, 2026-10-23); [ADR 0017](../../decisions/0017-goals-recurrence-investments.md) records the decisions. Design: [v2 README](../../design/v2/README.md) and `docs/design/v2/components.html` (Exists / Extend / New).

### Implementation plan
1. List every designed element, state and rule.
2. Check each in the app on the emulator and in the code and catalog.
3. Write the gap list and revise the following tickets; ask the owner about scope changes.

### Concrete cases and pitfalls
- Do not trust a document's claim that something exists: open the screen and the code.
- Do not start building while auditing; findings go into the list and tickets.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-085 --retain`.

### Execution progress

- **Audit, 2026-10-23:** [gap list](../../design/v2/gaps/metas.md) at `main` `d493c55`,
  each element checked in the code and, where drawn, in the catalog on the API-30
  emulator (screenshots). The goal components exist
  in the design system and catalog (SELLO-073, #72); nothing else does, and no branch or
  open pull request holds unmerged goal work.
- **Owner decision, 2026-10-23:** SELLO-089–094 revised to reuse those components and
  add only the listed extensions; no ticket removed.
