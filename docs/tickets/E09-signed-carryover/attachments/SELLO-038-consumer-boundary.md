# SELLO-038 — Boundary for storage, backup and UI

What `:domain` offers after SELLO-038 and what each consumer must give it. The rules are
[ADR 0020](../../../decisions/0020-signed-carryover-policy.md) (CP1–CP28); the expected
values are [signed carryover examples](../../../testing/signed-carryover-examples.md).
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
- `spending`: a `SpendingHistory` read at the same revision and financial day as the rest.
  A month inside the range with no expenses is a real zero; a month outside it is
  `MissingHistory`, never zero (CP6, CP8). The exact range depends on the call:
  - `carryIn`, a snapshot with `carryover`, and `drop`: only the chain that covers the
    month. From that chain's start month through the month before the month asked for
    (a snapshot) or through the current month (`drop`). Earlier, ended chains are not read,
    because a chain starts at 0: a category turned off and on again is read from its latest
    start. A gap inside that range fails.
  - `replay` and `CarryoverCorrectionReplay`: every chain they list, so from the earliest
    chain start whose months or settled balance are shown, through the month asked for
    (the current month for a correction preview).
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
- SELLO-041 owns every reader of `LimitedBudget.limit` that gives a wrong figure once carry
  can be turned on (review round 1, R3, ruled for SELLO-041 because no customer path builds
  a chain before then). `limit` is the capacity `C` when `carry` is present; `base` is the
  configured limit. With the correct figure for each:
  - `LimitAdjustmentPolicy.shortcuts`: the offered new base is the current **base** plus
    what is missing (`base − overall remaining` to cover the month, `base + shortfall` to
    cover payments), and for "set it to what was spent" the base that makes `C = S`, which is
    `S − K`. It uses `C` today (90.000 offered where 110.000 is needed).
  - `LimitAdjustmentPolicy.preview` and `MonthlyBudgetPolicy.limitTotalWith`: the total of
    bases only, so `left` omits `Σ K`; the overall capacity with a changed base is
    `Σ base + Σ K` (CP16).
  - `AdjustBudgetViewModel` (overall `limit`), `CategoryInsightsRenderModel` (category
    `limit`) and `ReciboRenderModel` (closed-month `limit` and its percentage): show `C`
    as "Límite con arrastre" with `Límite base` and `Arrastre` beside it (CP27), never `C`
    alone as "limit"; the remaining-week and remaining-day figures use `C` and `R` (CP19).
  Readers that are already right: `ResumenPolicy` (past verdict, the overall limit of the
  spending chart and the over-test `S > C`, all on `C`; Por asignar and the suggestion's
  limit and ranking on `base`), `ExpensePreviewPolicy`, `CategoryInsightsPolicy`,
  `CategoryOrderPolicy` (`base`).
