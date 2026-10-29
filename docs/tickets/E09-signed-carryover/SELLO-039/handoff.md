# SELLO-039 — Persist carryover configuration and atomic correction replay

Room version 7 adds `carry_chain` (category, start month, optional end month; no amounts), created empty by `Migration6To7`, so upgraded users stay off.
Commands: `EnableCarry`/`DisableCarry` and the drop, cap (CP25), past-month (CP13) and correction-fence (SC34) checks in `RoomCategoryCommands` and `RoomExpenseCommands`, through `CategoryCarry`/`expenseCarry` in `data/.../repository/CarryChecks.kt`.
Reads: carry is derived on every read, never cached, from only the history each call needs (`CarryRows.kt`); `RoomMonthlySnapshots` passes it to the snapshot, and the new `CarryoverReads` port (`RoomCarryoverReads`) gives a category's history and the CP18 catch-up notice.
`:domain` gained types and ports only (new commands, rejections, `OperationKind`s, `CarryoverReads`); see `artifacts/storage-contract.md`.
Ruling (review round 1, R1): catch-up keeps recording due payments even when carry cannot then be calculated; reads fail typed, and only a change whose result cannot be calculated is refused, so correcting the bad row repairs the chain; dropping an underivable balance stays refused. Corrections confirm with a `CorrectionConfirmation` bound to the change and revision (R2).
Open: SELLO-040 writes chains to and validates them in the backup (restore and reset already delete them); SELLO-041 binds `CarryoverReads`, handles the new rejections (including undo in a chain month, and the drop refused until a broken chain is repaired) and carries Resumen/category detail.
