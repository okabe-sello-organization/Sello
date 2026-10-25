# Inversiones brief

Designed in [Sello v2](../v2/README.md) as [Inversiones](../v2/inversiones.html); labels
and authority are in the [briefs README](README.md). The first question is “How are my
investments doing?” Newcomers should understand the overview, while experienced
users can reach the supporting detail without an intimidating first screen.

## Confirmed intent

- Lead with total portfolio value, not only contributed capital or gain.
- Show the gain/loss percentage with a direction arrow and green/red meaning, always
  with a sign and readable text.
- Below that answer, distinguish invested capital from the monetary gain/loss.
- Support it with the distribution across investments (by recorded value, largest
  first) and the history of contributions, withdrawals and dividends.
- Keep investments independent from goals, including when the user tracks the same
  money in both. Neither record allocates income.
- Use only user-entered information; never imply a bank connection or verification
  the app does not have.
- Make the age of recorded valuations visible on the overview as quiet context, so
  recent values can be told from older ones without opening each holding.

## Truthful presentation

Gain follows [architecture](../../../ARCHITECTURE.md): current value + withdrawals +
dividends − contributions, with the percentage as simple gain over contributions
(not IRR); zero contributions leave it undefined, not 0 %.

- Adding capital is not investment gain, and a withdrawal is not poor performance.
  Never derive the percentage from a bare change in portfolio value.
- If values cannot support a complete total, say so. Never render a partial
  portfolio as complete or a failed read as zero. The headline is recorded value,
  not verified wealth, and goals are never added to it as another asset.
- Mixed or unknown valuation dates never become a claim that every holding was
  valued at the latest update. Recording a movement is not proof of a fresh
  valuation. There is no automatic pricing or refresh service.

## Tone

The portfolio value uses the normal ink; restrained green/red applies to signed
performance, with the arrow and text carrying the same meaning. A negative return is
a fact, not an occasion for a reprimand. Use plain labels and optional explanations
for unfamiliar concepts. No universal investment target or investment recommendation.

## Scenarios

| Situation | Intended presentation |
| --- | --- |
| Contributed 100, value 110, no other cash flows | Owner example: total 110, +10 %, upward green arrow; capital 100 and gain 10 underneath. |
| Capital is added without a change in performance | The contribution is distinguished from return. |
| Performance is negative | Signed negative return, downward direction, red and readable context. |
| The return has no valid denominator | An understandable unavailable percentage, not 0 %. |
| Values are missing/stale or reading fails | The limitation is explicit; never a complete fresh portfolio or a zero. |
| The total uses older recorded valuations | Their age is visible on the overview at lower priority. |
| One contribution is also recorded against a goal | Both tools stay independent; category-only income allocation is unchanged. |

## Open

The return (simple, since the first contribution), COP and USD with one dated
app-wide manual rate, USD returns kept in USD, values never marked stale, dividends in
the gain and corrections with undo were decided in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md). The rules ticket (SELLO-106) settled the rest with worked examples in the
[investment examples](../../testing/investment-examples.md): dated cash flows, the value
between valuations, rate validation and the overview in pesos. Return periods stay out.

Inversiones belongs to the Later increment ([architecture](../../../ARCHITECTURE.md)
§1), separate from goals in Next. These notes authorize no routes, investment ledger,
FX services or dormant data models.
