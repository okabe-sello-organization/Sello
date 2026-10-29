# SELLO-038 — Boundary for storage, backup and UI

What `:domain` offers after SELLO-038 and what each consumer must give it. The rules are
[ADR 0020](../../../../decisions/0020-signed-carryover-policy.md) (CP1–CP28); the expected
values are [signed carryover examples](../../../../testing/signed-carryover-examples.md).
Nothing here is stored or shown yet, and no customer path turns carry on.

## What the domain owns

| Need | API (`com.software.sello.domain`) |
| --- | --- |
| Replay a category's chains, month by month | `SignedCarryoverPolicy.replay(input, currentMonth, through)` → `CarryReplay(months, settled)` |
| Carry-in of one month (null: no chain covers it) | `SignedCarryoverPolicy.carryIn(input, month, currentMonth)` → `CarryEntry?` |
| Turn on: refusals and the new chain list | `SignedCarryoverPolicy.enable(...)` → chains or `CarryEnableRefusal` |
| Drop a balance (off, archive, unlimited) | `SignedCarryoverPolicy.drop(input, currentMonth, confirmedCarryIn)` → `DropDecision` |
| Snapshot with `B`, `K`, `C`, `R`, verdict, allowance | `MonthlyBudgetPolicy.snapshot(..., carryover = inputs)`; `LimitedBudget.carry`, `LimitedBudget.base` |
| Reserve unrecorded payments of earlier chain months | `ReservationPolicy.reserved(schedules, month, chains)` |
| List of later months before saving a correction | `CarryoverCorrectionReplay.preview(currentMonth, corrections)` |
| Opening notice of catch-up | `CarryoverCorrectionReplay.catchUp(before, payments, currentMonth)` |
| Spending as the calculation reads it | `SpendingHistory.of(category, from, through, asOf, expenses)` |

## What a consumer must provide, per category

- `chains`: `CarryChain(category, start, end?)`, `end` exclusive, never overlapping (CP4).
- `defaults` and `overrides`: the category's whole base history (the same rows
  `CategoryBudgetPolicy.stateFor` reads), so a past month keeps the base it had.
- `spending`: a `SpendingHistory` read at the same revision and financial day as the rest,
  from the chain's first month to the month asked for. A month inside the range with no
  expenses is a real zero; a month outside it is `MissingHistory`, never zero (CP6, CP8).
- The current month and the financial day come from the `FinancialClock`; the domain reads
  no clock.

## Failures and where they surface

`CarryoverFailure` (`MissingHistory`, `NoFiniteBase`, `Unrepresentable`, `OverlappingChains`,
`InvalidChain`, `InvalidHistoryRange`, `FutureMonth`, `CurrencyMismatch`) comes from replay.
A snapshot maps `Unrepresentable` to `SnapshotFailure.Unrepresentable` and the rest to
`SnapshotFailure.Carryover`; a figure that needs carry is never base-only or zero (CP8).

## Left to later tickets

- SELLO-039: chain storage; CP13 (a past month in a chain cannot become unlimited); CP25
  checks on limit edits (`MAX_CHAIN_BASE`); commands, receipts, the preview fence (SC34).
- SELLO-040: chains in the portable file and their validation (SC40, SC42–SC45).
- SELLO-041: `LimitedBudget.limit` is the capacity `C` when `carry` is present. Screens that
  read it as the editable limit (`LimitAdjustmentPolicy`, the Recibo and Insights render
  models) must use `LimitedBudget.base` where they mean the base before carry is reachable.
  `LimitAdjustmentPolicy.preview` and `limitTotalWith` sum bases only.
