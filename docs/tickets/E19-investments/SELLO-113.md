# SELLO-113 — Release 0.2.0 with goals, recurrence and investments

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-094, SELLO-099, SELLO-100, SELLO-101, SELLO-102, SELLO-103, SELLO-104, SELLO-108, SELLO-111, SELLO-112, SELLO-004
- **Gate:** G3

### Outcome
A signed, accepted `0.2.0` with Metas, recurrence and Inversiones.

### Deliverables
A release checklist like the MVP's, the owner's walkthrough on a phone, measurements against the approved targets with the new features, and the go/no-go.

### Acceptance criteria
- Full G3 passes on the signed candidate.
- The owner walks goals, schedules and investments on a phone, including backup, restore and catch-up.
- Performance targets still hold with goals, schedules and investments in the large history.

### Tests
G3, the owner walkthrough and performance runs; G3.

### Working checkpoint
The owner approves `0.2.0`.

### Context and starting points
`docs/release/mvp-checklist.md` as the model; ADR 0016.

### Implementation plan
1. Checklist.
2. Walkthrough.
3. Go/no-go.

### Concrete cases and pitfalls
- Distribution stays a separate owner-authorized action.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-113 --retain`.
