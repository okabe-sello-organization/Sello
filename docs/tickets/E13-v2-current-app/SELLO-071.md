# SELLO-071 — Bring income entry, edit and history to v2

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-070, SELLO-064, SELLO-004
- **Gate:** G2

### Outcome
Income entry matches the v2 form in green, and income history becomes the v2
**Ingresos de octubre** screen: what came in, where it went, and each income.

### Deliverables
Income entry and edit on the SELLO-070 form (source chips without icons, **Otro**
with a full-width source-name row using the same composer, the amount in gain with
`+`, **Anotar ingreso**); income history rebuilt with `V2TitleBar`, an `AnswerSlip`
hero ("Entró en octubre"), a `SplitBar` with **En presupuestos de categorías** and
**Sin asignar** lines, and the income list with a total line.

### Acceptance criteria
- Income entry keeps today's sources, optional name, date, note, saving and
  recovery rules; the source name is required only for Otro and is never lost when
  the composer closes.
- The history hero shows the recorded total; a negative unassigned amount is signed
  and red, followed by the two facts behind it, with no suggestion.
- No expected income, plan box or suggestion appears: recurrence does not exist.
- Rows keep paging, editing and failed-page retry; loading and failed reads never
  show zero.
- A past month shows the shared ribbon.

### Tests
Income entry, history and journey tests updated; split-bar semantics; G2.

### Working checkpoint
From Resumen, income history shows the v2 hero and list for the selected month, and
a new income saved there appears in it.

### Context and starting points
Today: "5. Income history" and "7. Income entry and edit" in the current app
inventory; the allocated and unallocated figures already exist for Resumen. Reference:
`docs/design/v2/income.html`, "Income" in the v2 README and the catalog examples.

### Implementation plan
1. Move income entry onto the SELLO-070 form with the green tone.
2. Rebuild income history's top with the hero and split bar from existing figures.
3. Restyle the list; update tests.

### Concrete cases and pitfalls
- Unassigned income can be negative; the bar must not draw a negative segment.
- "Sello funciona sin ingresos" stays the empty state.

### Verification recipe
`:app` focused tests, then `./scripts/verify-ticket SELLO-071 --retain`.
