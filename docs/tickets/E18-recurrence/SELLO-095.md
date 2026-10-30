# SELLO-095 — Verify what exists for recurrence and reserved payments and what is missing

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G1

### Outcome
An exact, current list of what the app, the design system and the catalog already have for recurrence and reserved payments, against the approved design, so the following tickets build only what is missing.

### Deliverables
A gap list in `docs/design/v2/gaps/` for recurrence and reserved payments: each designed element marked present (with its code and tests), partial (what differs) or missing; the following tickets updated, split or closed to match, with owner approval for any scope change.

### Acceptance criteria
- Every element of the v2 reserved-payment states on Recibo and the Plan del mes on income is checked against the running app and the code, not only the docs.
- Each present element names its code path and test; each partial one says exactly what differs.
- Work done outside these tickets (other branches, merged PRs, catalog components) is found and credited.
- SELLO-096 to SELLO-104 are revised to the list before any starts; changed scope is recorded and owner-approved.

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
Focused tests, then `./scripts/verify-ticket SELLO-095 --retain`.

### Execution progress

- **Audit, 2026-10-24:** [gap list](../../design/v2/gaps/recurrence.md) at `main` `4d16b58`,
  each element checked in the code and, where drawn, in the app (sandbox scenarios
  `detalle-categoria@1`, `ingresos@1`) and the catalog on a dedicated API-30 emulator
  (screenshots). The reserved-state parts exist in the design system and
  catalog; nothing else does, and no branch or open pull request holds recurrence work.
- **Owner decisions, 2026-10-24:** one design pass for the screens v2 does not show, as
  the new SELLO-114 before SELLO-100, SELLO-101 and SELLO-103; SELLO-101 waits for
  SELLO-100 so each upcoming payment opens its schedule; SELLO-096 and SELLO-098 to
  SELLO-104 revised to the list. No ticket was removed.
