# SELLO-123 — Show where the income went on Resumen

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-100, SELLO-004
- **Gate:** G2

### Outcome
Resumen's income slip asks "¿A dónde fue lo que entró?" and answers it as a receipt: what
came in, the spending categories that took most of it, the others together and
Ingresos menos gastos. Under a tear line, a stub opens the income list and Pagos e
ingresos fijos, each with one fact, as in the mockup in `artifacts/`.

### Deliverables
- The snapshot gives the month's recorded income count and its spending by category as
  the three categories that took most and the rest together, summed exactly.
- Pagos e ingresos fijos' policy gives the nearest date still to come among active
  schedules.
- The slip: Entró en {mes}, a bar of spent against the rest of the income, a line per
  top category with its icon, taken off ("− 200.000") and opening that category, "Otras N
  categorías", the Ingresos menos gastos total, and the date the figures run to.
- A stub part in the design system: two halves under the tear line, each an icon, a
  title and one fact, each one button; with catalog examples and previews.
- The stub's facts: "3 en octubre" (or "Ninguno en octubre") and "Próximo: hoy",
  "mañana", "28 oct" (or "Ninguno activo"); no fact while schedules load or fail.
- The v2 design reference's Resumen paragraph describes the new slip.

### Acceptance criteria
- Amounts are exact: the top three and the rest add up to the month's spending, and the
  total is the existing Ingresos menos gastos. Nothing is projected or estimated.
- Spending above income keeps the total signed and red, with "Anotaste X de ingresos y Y
  de gastos."; the bar is full.
- No income recorded in the month shows "Aún no anotas ingresos en {mes}." and no
  negative figure; three categories or fewer show no Otras line.
- Ties in spending keep the order the ranking below ("¿Dónde se va?") uses.
- A failed schedule read never says "Ninguno activo": the half keeps its title only.
- Loading, failed read, stale figures, a past month, large text, both themes and the
  two-pane layout keep working; every target is at least 48dp and reads as one button.

### Tests
- Domain host: the income count and the top three with the rest, from independent
  examples (ties, fewer than four, nothing spent); the nearest active date (paused and
  ended left out, one already due counts as today's).
- App host: the render model's lines, the Otras count, the negative case and the stub's
  facts; the view model's schedule fact for ready, loading and failed reads.
- Design-system device: the stub's two buttons, their spoken labels and large text.
- App device: the slip's lines and links on Resumen, and the journeys that open the
  income list and Pagos e ingresos fijos from it.

### Working checkpoint
On the running app the slip looks like the mockup in both themes.

### Context and starting points
`feature/resumen/ResumenScreen.kt`, `ResumenRenderModel.kt`, `ResumenViewModel.kt`;
`ResumenPolicy` and `ResumenSnapshot` in the domain; `ScheduleListPolicy`; `LeaderLine`,
`TotalLine`, `SplitBar` and `TearLine` in the design system; the mockup in
`artifacts/maqueta.html`; ADR 0012 BG4.

### Implementation plan
1. The domain: income count, top categories with the rest, the nearest active date.
2. The stub in the design system, with catalog examples.
3. The render model, the view model's schedule fact and the slip.
4. The device tests and journeys that used the old lines and links.

### Concrete cases and pitfalls
- A category with spending but no limit still counts; an archived one with spending too.
- Future-dated income is not counted, as the income total already excludes it.
- A schedule due earlier but not yet recorded by catch-up says "Próximo: hoy", never a
  past date.
- Goal and investment records are never shown or subtracted here (ADR 0012 BG4).

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-123 --retain`.

### Execution progress

- **2026-10-26:** Built as follows, proposed for review with this ticket's pull request.
  The domain adds the month's income count and `IncomeDestinations` (the three
  categories that took most, ties in category order, and the rest summed exactly) to the
  Resumen snapshot, and `ScheduleListPolicy.nextDate` (the nearest date among active
  schedules, an overdue one counting as today). Resumen's income slip asks "¿A dónde fue
  lo que entró?" and reads Entró, a spent/rest bar, the top three taken off (each opens
  its category), Otras N categorías and Ingresos menos gastos, red with both facts when
  spending is higher; with no income it says so. The new `SlipStub`/`StubButton` in the
  design system end it: Ingresos ("3 en octubre") and Fijos ("Próximo: 28 oct", hoy,
  mañana, Ninguno activo, or nothing while schedules cannot be read). Sin asignar stays on
  the income screen. The catalog's income example and the v2 Resumen paragraph follow.

