# SELLO-038 — Implement exact signed carryover domain calculations

The calculation is in `:domain`: `SignedCarryoverPolicy` (replay, carry-in, enable, drop), `CarryoverCorrectionReplay` (preview, catch-up notice) and the `carryover` input of `MonthlyBudgetPolicy.snapshot`; types are in `model/Carryover.kt`.
`LimitedBudget.limit` is the capacity `C` when `carry` is set; `LimitedBudget.base` is the configured limit. Por asignar and the limit ordering already use `base`.
Nothing is stored, wired or reachable by a customer. Storage must pass chains, base history and a `SpendingHistory`; see `artifacts/consumer-boundary.md`.
Open: SELLO-039 (storage, commands, CP13, CP25 on limit edits, preview fence), SELLO-040 (portable chains), SELLO-041 (screens and `LimitAdjustmentPolicy` / render models must use `.base` where they mean the base).
