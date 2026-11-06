# Sello design philosophy

**Updated:** 2026-11-05 · **Status:** approved design direction; implementation pending.

## Purpose and working motto

**Tu presupuesto al servicio de tus metas.**

Sello helps people build a realistic budget, adjust it as life changes and reach
personal goals. Success means a plan that fits; recreation is a valid use of money.
The motto is working wording, not final interface copy.

## Authority and scope

The [design system](system/index.html) is the visual reference for screens and
components ([ADR 0026](../decisions/0026-design-system-from-the-built-app.md)); it is
written from the built app, and where the app and an older design differ, the app wins.
[Architecture](../../ARCHITECTURE.md) owns financial, recovery, privacy and module rules.
[ADR 0011](../decisions/0011-goal-centered-design.md) records product direction,
[0012](../decisions/0012-budget-guidance-contract.md) defines budget and income meanings,
and [0017](../decisions/0017-goals-recurrence-investments.md) records the rules for goals,
recurrence and investments.

MVP has Recibo and Resumen; goals, recurrence, advanced insights and investments
come later. Design approval does not mean implementation. Replace financial
calculations through scoped work with approved independent examples and tests.

## Principles for every screen

1. **Show what matters first.** Lead with the answer; keep supporting detail secondary.
2. **Keep actions visible and stable.** Use clear hierarchy and fewer interruptions.
   Do not hide essential controls or add steps merely to make a screen look simpler.
3. **Make everyday recording quick.** Give brief feedback after a confirmed save
   and return. Preserve durable receipts, recovery and protective confirmations.
4. **Give useful, gentle guidance.** Base suggestions on relevant history and offer
   a clear next step. Do not impose a universal savings ratio or blame for changed plans.
5. **Celebrate meaningful progress.** Use small milestone celebrations and a larger
   one for goal completion. Respect reduced motion and preserve honest progress.
6. **Keep Sello's identity.** Reuse paper, stamps, category circles, bundled type
   and Cobalto/Violeta light/dark themes through existing tokens and components.
7. **Make meaning accessible.** Show exact amounts and use words alongside color.
   Support large text, keyboards and different window sizes from the start.

## Screen responsibilities

### Home / Recibo

Keep Home short and easy to scan. Lead with remaining budget and one secondary
overall daily guide, independent of income and spending-pace forecasts. Categories
keep their own remaining/overrun figures, without prescribed daily caps or automatic
reallocation. Future payment reservations follow ADR 0012 when recurrence ships.

When Metas ships, show one compact goal between the summary and categories, with
progress, detail and Aportar access. Keep the user's selection; otherwise default
to the active goal with the highest completion percentage. A small early-month
invitation can lead to Resumen; keep detailed advice there and avoid a feed of cards.

### Expense and income entry

One tap from Home opens expense entry with the amount ready. Keep category/source
selection fast, the note easy to find and the main save action in a stable place.
Amounts use the Sello pad; text uses the phone keyboard. Notes use a composer docked
to the keyboard, returning to the same form when closed. Keep expense/income drafts
separate, validate original input and show busy/error states. Use no live financial previews.

### Resumen

Lead with the same-days comparison, then explain patterns and useful adjustments:
changes across months, repeated category overruns and weekday spending. Put net
flow in the income slip below the comparison. Keep insights few, supported by
history and actionable; an isolated category overrun in a healthy overall month
does not require intervention. Avoid repeating advice across screens.

### Income and allocation

Show how recorded income is assigned through category budgets. Offer an optional
budget adjustment for unassigned income, without requiring complete allocation.
Goal/investment entries track progress separately; they do not reduce unassigned
income. Unspent budget is not automatically spare money. Expected recurring income
belongs to later planning work under ADR 0012.

### Metas and Inversiones

Add these destinations when their features ship: four tabs at most, three preferred
where equally clear. Goals are user-defined, accept progress without bank proof and may
have no deadline. Keep history and completed goals accessible. Milestones, completion,
undo and withdrawal bounds follow the [goal examples](../testing/goal-examples.md); screen visits are not milestone crossings.

Goals and investments stay independent, even for the same money. Do not combine
them into a wealth total or automatically create entries across tools. Inversiones
leads with value, return and valuation age. Contributions are not gains; recorded
values must not imply live or bank-verified balances.

## Tone and feedback examples

Use short Spanish, exact facts and a useful next step. Pair signed remainders with
words and restrained color; statuses follow ADR 0012 and currency display follows
[ADR 0007](../decisions/0007-no-peso-symbol.md). Explain repeated overruns with a budget
review suggestion; offer goal-plan adjustments without blame.

## Open implementation decisions

Questions no ADR, worked example or the code answers yet:

- Budget example BG-E12 in [ADR 0012](../decisions/0012-budget-guidance-contract.md)
  (income assigned to a user-created investment category) is a derived illustration
  the owner has not reviewed on its own.
- Entry on a real device: focus, keyboard insets, TalkBack and hardware keyboards on
  the Sello pad and the note composer have not been confirmed.
- Inversiones: the first version shows only the simple return since the first
  contribution; whether return periods or an annual rate come later is not decided.

## Review a new screen

Check its purpose, main answer and obvious next action. Review keyboard-open,
large-font and constrained layouts, plus loading, empty, failed, stale, busy and
restored states. Confirm exact financial meaning, supported suggestions and
accessible controls.
