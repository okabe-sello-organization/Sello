# 0017 — Product decisions for goals, recurrence and investments

**Owner:** project owner (user) · **Date:** 2026-10-23
**Status:** owner decisions from a design interview; each epic's rules ticket turns them
into reviewed worked examples before code.

## Context

[Sello v2](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/v2/README.md) ([ADR 0013](0013-sello-v2-design.md)) designs Metas,
reserved payments and Inversiones, and the briefs left their product rules open. After
the MVP (SELLO-036) the owner asked for epics for the missing parts and an interview on
those rules. This records the answers; [architecture](../../ARCHITECTURE.md) and ADRs
0001–0016 still own money, dates, recovery and safety.

## Order and shape

- Order: the small v2 gaps (E15, E16), then Metas (E17), recurrence and reserved
  payments (E18) and Inversiones (E19). Signed carryover (E09) and legacy import (E10)
  keep their own plans.
- Main tabs once these exist: Recibo, Resumen, Metas, Inversiones; at most four.
- Metas, recurrence and Inversiones ship together in one release, `0.2.0` (E19's last
  ticket), not one release each.
- Each epic opens by verifying what already exists in the app and the component catalog
  against the design, because the screens have been worked on outside these tickets.

## Metas

- A contribution or withdrawal recorded by mistake can be edited or deleted, with undo,
  like an expense; percentage and stamps follow the corrected total.
- Changing a goal's target recomputes the percentage. A stamp earned under the old
  target and no longer reached stays faded as a past achievement; newly reached ones
  light up, as after a withdrawal.
- When Recibo's goal is completed or deleted, Recibo falls back to the active goal with
  the highest completion until the person chooses another; nothing when none is active.
- No pausing in the first version.
- Movements may be dated in the past (a date pill like expenses), never in the future.
  A stamp is earned when the movement is recorded, whatever its date.
- No limit on the number of active goals.

## Recurrence and reserved payments

- Schedules: monthly on a day (a month without that day uses its last day), twice a
  month, and every N days or weeks from a start date.
- On its date a recurring payment or income is **recorded automatically**. Until then a
  payment is reserved (Recibo's headline is after reservations, ADR 0012) and an income
  is expected (Plan del mes).
- Recording happens when Sello opens, catching up: no background work, no new
  permission. Every missed occurrence is recorded on its own date, bounded, and a notice
  lists what was recorded so each can be checked, edited or deleted.
- Recurring incomes follow the same rules as payments.
- Changing a schedule affects future occurrences only; recorded ones stay and each can
  still be edited on its own.
- A schedule can be paused, resumed and given an optional end date. While paused it
  reserves and records nothing, and occurrences inside the pause are skipped, not caught
  up.
- No notifications for now.

## Inversiones

- The overview shows the simple return since the first contribution: (value +
  withdrawals + dividends − contributions) ÷ contributions. No annual rate or periods
  in the first version; no contributions means no percentage, not 0 %.
- Currencies: COP and USD. USD uses **one app-wide rate the person types, with its
  date**; changing it records a new dated rate. A USD investment's contributions, value
  and return stay in USD; only the portfolio total converts to COP at the current rate.
  No network or automatic rates.
- A recorded value shows its date; it is never marked stale.
- Dividends or rent count in the gain, not in the value; a reinvested dividend is also
  recorded as a contribution.
- Movements recorded by mistake can be edited or deleted with undo; totals and return
  follow the corrected history.

## Small v2 gaps (E16)

- Recibo's **Ver cómo cerraste …** shows on days 1–7 when the previous month had
  expenses, and is gone for that month once opened or dismissed.
- **¿Qué días gastas más?** averages spending per weekday over the last six months,
  the selected one included, or from the first recorded day when that is later, and
  says which (owner, 2026-10-23, replacing "within the selected month" after seeing
  both on a phone).
- A dismissed Resumen suggestion may return the next month if still true.

## Consequences

The briefs' open lists shrink to the technical questions the rules tickets answer with
examples (milestone replay safeguards, catch-up bounds, rate validation). Recurrence
changes ADR 0012's "recurringDue is zero" for its release, and USD changes the MVP's
COP-only contract for Inversiones only; both arrive through their tickets, not here.
Background work, notifications and network stay out.
