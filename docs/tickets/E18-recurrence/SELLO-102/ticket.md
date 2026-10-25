# SELLO-102 — Show expected income in Plan del mes

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-098, SELLO-004
- **Gate:** G2

### Outcome
Income history shows the v2 Plan del mes with expected incomes until they are recorded.

### Deliverables
The dashed plan box with Anotado, Esperado, Planeado and Sin asignar en el plan, and expected-income chips. None of its parts exists ([gap list](../../../design/v2/gaps/recurrence.md)); the design system gains a dashed plan slip with its **PLAN DEL MES** tag, a softer and a bold `LeaderLine`, a single-rule total and an expected-income chip, each in the catalog and in `docs/design/v2/components.html`.

### Acceptance criteria
- Expected money never enters recorded totals.
- An expected income becomes recorded on its date automatically.
- No plan box for a past month or without expected income.
- The catalog shows each new part in all themes and at large text.

### Tests
ViewModel and screen tests; device tests for the new parts; G2.

### Working checkpoint
Plan del mes shows expected salary until its date.

### Context and starting points
v2 Income; `IncomeHistoryScreen` and the `IncomeHistoryScreenTest` assertion that no **Plan del mes** exists yet. `ReviewInvitation` and `AddChip` draw a dashed outline in their own code; reuse it rather than drawing a third.

### Implementation plan
1. Plan box.
2. Chips.
3. Tests.

### Concrete cases and pitfalls
- Only shown when expected income exists.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-102 --retain`.

### Execution progress

- **Owner decision, 2026-10-24:** the footnote says "Lo esperado se anota solo en su
  fecha." (catch-up records it; the person does not), replacing the reference's "Lo
  esperado pasa a anotado cuando lo registras." in the README and `income.js`.
- **Rule:** `IncomePlanPolicy` (domain) takes each income schedule's occurrences still to
  come this month after its checkpoint, and sums by BG3: Planeado `I + F`, Sin asignar
  en el plan `I + F − A`, exact, an overflow a failure. Nothing expected, no plan.
- **Screen:** `IncomeHistoryViewModel.plan` shows it for the month in progress only, and
  only when the month's summary and the schedules were read at the same revision, so an
  income catch-up has just recorded is never both recorded and expected. A failed read
  is a failed plan with Reintentar, never no plan. The plan sits under the hero, or under
  the empty slip when nothing is recorded yet.
- **Design system:** `PlanSlip`, `ExpectedChip`, `LeaderLine` emphasis (`Soft`,
  `Strong`) and `TotalLine` rule (`Single`); `AddChip` now uses the same dashed outline.
  Catalog *Plan slip and expected chip* (all themes, large-text previews) and three rows
  in `components.html`.
- **Tests:** domain `IncomePlanPolicyTest` (5: RE35/BG-E08, RE36/BG-E09, which schedules
  count, overflow, a negative plan); `IncomeHistoryViewModelTest` (+7); device
  `PlanPartsTest` (3) and `IncomeHistoryScreenTest` (+5). Red: the policy tests failed on
  the stub; deliberate breaks of the revision check, the month check, a failed read
  shown as none, the sign, the tag's description, a tappable chip and the plan not drawn
  each fail a test.
