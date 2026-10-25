# SELLO-116 — Fence schedule writes in the sandbox

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-098, SELLO-004
- **Gate:** G2

### Outcome
In debug builds, schedule commands and catch-up go through the sandbox's write fence like every other financial write, and schedule commands meet its faults and log.

### Deliverables
Fenced and fault-injecting schedule commands, a fenced catch-up store, schedule receipts in the diagnostic's operation lookup, and their bindings in the debug composition.

### Acceptance criteria
- A schedule command or a catch-up batch started after the fence closed never reaches storage.
- A new schedule change or a recorded catch-up batch marks the sandbox changed; a replay, a rejection, an unknown outcome or an empty batch does not.
- Armed faults apply to schedule commands and are logged; the diagnostic finds their receipts.
- Release builds are unchanged.

### Tests
Unit tests of the decorators; a device test of the sandbox's real graph; G2.

### Working checkpoint
The sandbox's graph returns the fenced schedule commands and catch-up store.

### Context and starting points
Found in SELLO-094 on 2026-10-25: goal writes were fenced there, schedule writes were not. Owner decision 2026-10-25: do it now, in its own ticket, so SELLO-104 can concentrate on recurrence scenarios. Architecture §9; `FencedCommands.kt`, `FaultyPorts.kt`.

### Implementation plan
1. Red: pass-through decorators fail the fence, fault and diagnostic tests.
2. Decorators and bindings.
3. Graph test.

### Concrete cases and pitfalls
- Catch-up has no operation of its own: it is fenced, not fault-injected or logged.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-116 --retain`.
