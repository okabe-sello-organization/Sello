# 0002 — Preserve surplus and overspending across periods

**Owner:** project owner (user) · **Updated:** 2026-10-16
**Status:** approved post-MVP direction; the detailed policy is
[ADR 0020](0020-signed-carryover-policy.md), approved 2026-10-28.

Carryover preserves both unused budget and overspending across months. Neither
may disappear silently. MVP renews configured monthly budgets without carryover;
do not add unused tables, jobs, settings or UI for it. Renewal changes a spending
plan, not actual money or bank balances.

Keep monthly base, carry-in and resulting capacity distinct and explainable.
Carryover is neither income nor expense; do not count it in allocation or net flow.

## Simple examples

Assume one category, a monthly base of 100,000 COP, no previous carry, no caps or
transfers, and carryover enabled:

| Example | Spending | Carry into next month | Next month's capacity |
| --- | --- | --- | --- |
| C01 | 20,000 | +80,000 | 180,000 |
| C02 | 120,000 | −20,000 | 80,000 |

These two rows are one month each; the multi-month formula and its worked examples are
in [ADR 0020](0020-signed-carryover-policy.md). Preserve earlier deficits, even
when allowance is zero; never silently clip, expire or forgive them.

## Policy decisions

SELLO-037 settled what this ADR left open, in [ADR 0020](0020-signed-carryover-policy.md)
with [worked examples](../testing/signed-carryover-examples.md):

- Scope and start: per category, opt-in, off by default, starting at 0 in the month it is
  turned on (CP1–CP4); the continuous signed formula with no cap (CP5–CP8).
- Dropping a balance only with an exact, confirmed settlement when turning carry off,
  archiving or making a category unlimited; zero and negative capacity, missing history and
  overflow (CP9–CP13, CP23–CP25).
- Verdicts, remaining and allowance against base + carry; income allocation on base limits
  only (CP14–CP19).
- Correcting an older expense: a preview of every later month that changes, then one
  recalculation; base limits never change (CP20–CP22).

## Implementation and verification

Follow [architecture](../../ARCHITECTURE.md) for module and financial boundaries.
Preserve dated transactions, category identities and past limits. Delivery belongs
to [SELLO-E09](../tickets/E09-signed-carryover/README.md), SELLO-037–043, after
SELLO-036 release acceptance. After policy approval, verify real-Room atomicity,
replay/restart, backup/migrations, sandbox time scenarios and accessible capacity
breakdowns. Cover skipped months, late corrections, mode transitions and overflow.
