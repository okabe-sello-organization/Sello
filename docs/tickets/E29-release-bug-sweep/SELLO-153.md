# SELLO-153 — Cambiar la tasa never locks after an unknown save

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
After the dollar rate's save comes back unknown, the pad is locked and the form only
offers to check again, so it can never get stuck "saving".

### Acceptance criteria
- Submit → unknown → typing a digit changes nothing and keeps the unknown message;
  "Comprobar de nuevo" asks with the same operation ID; Back stays refused until the
  outcome is known.
- No sequence of actions leaves `busy` on with nothing in flight.
- Red-first JVM tests.

### Notes
Audit findings: `UsdRateState.kt:59` (`editable` ignores Unknown),
`UsdRateEntryViewModel.kt:157-158` (clears Unknown), `:225` (`settle(null)` keeps busy).
Review: lighter.
