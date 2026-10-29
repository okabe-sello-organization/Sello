# SELLO-069 — Wire the confirmation notice into the shell

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-007, SELLO-004
- **Gate:** G2

### Outcome
The shell can show `SelloNotice`'s confirmation look (built in pull request #54)
above the dock, ready for SELLO-066 to use after a save.

### Deliverables
The shell's notice slot accepting a confirmation notice, placed above the dock on
every tab and beside the rail in wide layouts, with a shell test.

### Acceptance criteria
- A confirmation shows the gain check before the message, e.g. "Gasto anotado:
  48.700 en Comida"; the check is decorative and not spoken.
- The message is announced politely; spoken text can carry the full amount.
- Existing undo and plain notices look and behave as before.
- Correct in light and dark themes, at font scale 2.0 and with the keyboard closed.

### Tests
Shell notice placement and semantics tests in both themes; G2.

### Working checkpoint
A test host shows a confirmation above the dock on Recibo and Resumen.

### Context and starting points
`SelloNotice(confirmation = true)` is in `designsystem/component/Corrections.kt` with
its catalog example in the V2 feedback examples. The shell's notice slot is the
`notice` parameter of `SelloScaffold`. Timing and text belong to SELLO-066.

### Implementation plan
1. Pass confirmation notices through the shell's notice slot.
2. Add placement and semantics tests.

### Concrete cases and pitfalls
- Do not add timing or auto-dismiss to the component; the shell owns it.
- The notice must not cover the dock or the tab bar.

### Verification recipe
`:design-system` and shell device tests, then
`./scripts/verify-ticket SELLO-069 --retain`.
