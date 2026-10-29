# SELLO-117 — Keep category amounts whole in Recibo's grid

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
A category with no limit shows what was spent on its own line, with a small "gastado" under it, and the amount never breaks apart in Recibo's grid.

### Deliverables
`CategoryCell` puts the amount on its own line and its caption under it, as in the approved v2 Recibo grid; Recibo, the catalog and the prototype pass the caption.

### Acceptance criteria
- In a grid column of a 320dp screen, "120.000 gastado" keeps the amount whole: the cell is as tall as with room to spare.
- The cell is still one button that says the whole category, as before.
- Cells with a limit look as before.

### Tests
A device test of the cell narrow and roomy in all four colour schemes; the existing category semantics and Recibo tests; G2.

### Working checkpoint
A past month at 320dp shows "120.000" whole with "gastado" under it.

### Context and starting points
Found in SELLO-093 on 2026-10-24 (a past month at 320dp showed "12 0. 00 0"); owner decision 2026-10-25: follow the v2 grid (`docs/design/v2/home.js`, `.cell-text` in `sello.css`).

### Implementation plan
1. Red: the narrow cell grows as the amount breaks up.
2. The amount on its own line, the caption under it.
3. Callers and the related tests.

### Concrete cases and pitfalls
- Never shorten the amount with an ellipsis; it must stay exact.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-117 --retain`.
