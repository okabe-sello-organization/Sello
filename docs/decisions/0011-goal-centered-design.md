# 0011 — A budget that serves the user's goals

**Owner:** project owner (user) · **Updated:** 2026-10-16
**Status:** approved product direction; implementation and some financial rules
remain pending. [ADR 0013](0013-sello-v2-design.md) owns the final v2 screen choices.
Since 2026-11-05 the design source is [ADR 0026](0026-design-system-from-the-built-app.md).

Sello helps people build a realistic, predictable budget and reach personal goals.
Spending less is not a universal measure of success. The working motto is
**Tu presupuesto al servicio de tus metas**; it is editorial, not final interface copy.
The [design philosophy](../design/philosophy.md) explains the screen principles.

## Product direction

- Preserve paper, stamps, fonts, inks and category circles, with exact amounts and
  accessibility. Keep essential actions visible, stable and easy to discover.
- Home leads with budget margin after known upcoming payments, with reservations
  and overall daily guidance at lower priority. Its target verdict uses facts,
  not spending-pace extrapolation. One category's overrun need not mean an overall
  budget overrun; never silently reallocate limits.
- Amount entry uses the Sello pad; text uses the phone keyboard and notes use a
  docked composer. Ordinary expense/income saves give brief confirmed feedback.
  Durable receipts and recovery remain required, as do protective confirmations
  for discard, archive, restore/reset and other sensitive actions.
- Resumen leads with comparisons and explains patterns and useful adjustments.
  Keep insights few, factual, gentle, actionable and supported by relevant history.
  Net flow, budget remaining and allocation keep their separate meanings.
- Only category budgets allocate income. Expected and recorded income stay distinct;
  goal/investment records do not independently reduce unassigned income.
- Prefer three clear main destinations where feasible, with at most four:
  Recibo, Resumen, Metas and Inversiones as capabilities ship. MVP has only the
  working Recibo and Resumen tabs; designs alone do not expand release scope.

## Goals and investments

Goals are user-defined, with optional target dates. Recorded contributions count
as progress without bank proof; withdrawals preserve contribution history.
Goals and investments are independent tracking tools, even for the same money.
They create no automatic category/expense/income entries or overlap deductions.

When Metas ships, show one compact goal on Home. Respect the user's selection;
otherwise choose the active goal with the highest completion percentage. Small
celebrations mark genuine milestone crossings, including later re-crossings; goal
completion earns the larger celebration. Redrawing or reopening a screen is no
new crossing. Completed goals remain accessible in Completadas. Completion,
earned-stamp display and withdrawal bounds follow ADR 0013.

Inversiones leads with portfolio value and clearly identified return, with quiet
context showing the age of recorded valuations. It does not verify bank balances.

## Implementation inputs

[ADR 0012](0012-budget-guidance-contract.md) defines confirmed budget/income rules
and remaining cases. Existing D03 calculations, rounding and tests remain in force
until reviewed replacement rules and independent examples are implemented.

Later inputs include recurrence lifecycle, featured-goal ties/invalid selections,
early-month review-invitation timing,
goal corrections and recovery of milestone events, and investment ledger,
return-period, dividend and FX policies. The
[screen briefs](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/screen-briefs/README.md) hold the feature-specific questions.

This direction introduces no daily category caps, universal savings target,
automatic reallocation or bank verification. Exact money, budget history,
zero/unlimited limits, ordering, guarded undo, local-only privacy and module
boundaries remain governed by [architecture](../../ARCHITECTURE.md) and the
[product contract](0001-mvp-contract.md).
