# SELLO-184 — Every screen tells its news with the status slip

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-181
- **Gate:** G2

### Outcome
Every empty, failed, older, unknown or gone state uses `StatusSlip` with the kind that
means it, so no screen shows "No se pudo cargar" above figures that loaded.

### Acceptance criteria
- Every EmptySlip, ErrorSlip and StaleSlip call and their hand-built copies (C06, C07)
  use `StatusSlip`; the three old components are deleted.
- Fixes M01, M12-1 to M12-4 and M13; the findings page no longer lists them.
- Re-recorded baselines; the feature pages describe the new states.
