# SELLO-079 — Verify what exists for today's Recibo, Resumen and income screens and what is missing

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-004
- **Gate:** G1

### Outcome
An exact, current list of what the app, the design system and the catalog already have for today's Recibo, Resumen and income screens, against the approved design, so the following tickets build only what is missing.

### Deliverables
A gap list in `docs/design/v2/gaps/` for today's Recibo, Resumen and income screens: each designed element marked present (with its code and tests), partial (what differs) or missing; the following tickets updated, split or closed to match, with owner approval for any scope change.

### Acceptance criteria
- Every element of the v2 Recibo, Resumen and income pages is checked against the running app and the code, not only the docs.
- Each present element names its code path and test; each partial one says exactly what differs.
- Work done outside these tickets (other branches, merged PRs, catalog components) is found and credited.
- SELLO-080 to SELLO-084 are revised to the list before any starts; changed scope is recorded and owner-approved.

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
Focused tests, then `./scripts/verify-ticket SELLO-079 --retain`.
