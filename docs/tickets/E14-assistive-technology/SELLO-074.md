# SELLO-074 — Review the app with TalkBack, Switch Access and a keyboard

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-073, SELLO-004
- **Gate:** G2

### Outcome
A person has used every screen with TalkBack, with Switch Access and with a hardware
keyboard on a device, and what they found is fixed or ticketed.

### Deliverables
A recorded manual pass (device, Android version, settings, screens and findings) in
`docs/testing/`, with fixes for small issues and tickets for larger ones.

### Acceptance criteria
- Every screen and state in the released scope is covered: Recibo, entry (expense
  and income, including the composer and unknown outcome), income history, Resumen,
  category detail and editor, Configuración, Respaldo, Borrar, the start-up failure
  screen and the frame.
- Amounts are spoken exactly; status changes are announced once; focus order follows
  the visual order; every action is reachable by switch and by keyboard.
- Findings are fixed with a test or ticketed; none are left unrecorded.

### Tests
The manual pass itself; regression tests for each fix; G2.

### Working checkpoint
The recorded pass lists no open, unrecorded finding.

### Context and starting points
Accessibility baseline: "Adaptive and accessibility baseline" in the current app
inventory and ARCHITECTURE.md. Automated tests already cover semantics and target
sizes; this ticket is the human check they cannot replace.

### Implementation plan
1. Run the pass with TalkBack, then Switch Access, then a hardware keyboard, on a
   physical device.
2. Record findings; fix small ones with tests; ticket the rest.

### Concrete cases and pitfalls
- The celebration and ribbons use live regions; check they are not announced twice.
- Use synthetic data only; no personal records on the review device.

### Verification recipe
The recorded pass, focused tests for fixes, then `./scripts/verify-ticket SELLO-074 --retain`.
