# Screen and interaction briefs

Each brief records a screen's job, the owner's confirmed intent and the questions
still open. The final design is [Sello v2](../v2/README.md)
([ADR 0013](../../decisions/0013-sello-v2-design.md)); the briefs keep the intent behind
it and the policy it leaves open.

| Brief | Covers |
| --- | --- |
| [Home / Recibo](home.md) | The month's answer, reserved payments, status and the review invitation |
| [Expense and income entry](entry.md) | The shared recording journey, the Sello pad and the note composer |
| [Resumen and income](summary-and-income.md) | Comparisons, patterns, net flow and expected income |
| [Metas](goals.md) | Goals, contributions, withdrawals, milestones and completion |
| [Inversiones](investments.md) | Holdings, valuation age and future return mechanics |

## Open questions

| Screen | Boundary before affected implementation |
| --- | --- |
| Home and recurring payments | Independent examples for the ADR 0012 rows not yet separately reviewed (BG-E01, BG-E04, BG-E12); review-invitation timing; featured-goal ties. |
| Entry | Resolve note-composer Back/dismissal/restoration and validate actual pad/IME/inset/accessibility behavior. |
| Resumen and income | None for expected income: settled in the [recurrence examples](../../testing/recurrence-examples.md). |
| Metas | v2 settles thresholds (25 / 50 / 75 % and Meta), withdrawal bounds, manual completion with Deshacer and read-only completed goals (ADR 0013). Still open: corrections, target edits, the Home-pin lifecycle, pausing and milestone replay safeguards. |
| Inversiones | Return periods, dated cash flows, valuation dates/sources, movement corrections, dividends and currency/rate policy. Visible valuation age does not settle those mechanics. |

These are inputs for later work, not permission to reopen answers already recorded.

## Labels

- **Confirmed intent** records the owner's answers. Preserve it when designing.
- **Recommendation** is a proposed way to achieve that intent; it can change during
  review without being misrepresented as an approved requirement.
- **Open** needs an answer or a worked example before dependent implementation.
- **Designer discretion** covers visual choices within the agreed intent. If a layout
  cannot meet the behavior, raise that tradeoff rather than silently changing the
  product requirement.

## Authority

- Where a brief and v2 differ, v2 and ADR 0013 apply; open questions stay open.
- [ADR 0012](../../decisions/0012-budget-guidance-contract.md) owns the financial
  definitions, [architecture](../../../ARCHITECTURE.md) the safety and technical
  contracts, and the [philosophy](../philosophy.md) the common direction.
- The [v1.1 reference](../README.md#preserved-v11-visual-reference) covers only what
  v2 does not show.
- Keep receipt paper, stamps, bold exact numbers, category circles, bundled fonts and
  the Cobalto/Violeta light/dark identity.

Product direction is not implementation approval. The briefs create no tickets,
status changes or release-scope expansion.
