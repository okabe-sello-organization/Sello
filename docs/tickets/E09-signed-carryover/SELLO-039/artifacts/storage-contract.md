# SELLO-039 — Storage and read contract for carryover

What `:data` stores and offers after SELLO-039, for SELLO-040 (backup), SELLO-041 (UI) and
SELLO-042 (sandbox). The rules are [ADR 0020](../../../../decisions/0020-signed-carryover-policy.md)
(CP1–CP28); the domain boundary is SELLO-038's
[consumer boundary](../../SELLO-038/artifacts/consumer-boundary.md). No customer path turns
carry on: nothing in `:app` submits the new commands or reads the new port.

## Schema (version 7)

- One new table, `carry_chain(category_id, start_month, end_month?)`, primary key
  `(category_id, start_month)`, foreign key to `category` with `RESTRICT`. A row is a chain
  of CP4: months from `start_month` up to, not including, `end_month`, or open while it is
  null. No amount is stored: carry, capacity, remaining and the settled balance are derived
  (CP7, CP12). A row that ends in or before its start is damage and fails the read.
- `Migration6To7` only creates the empty table. Every version 6 row is kept as it was, and
  every existing category is off (CP1). No index is added: the spending read uses the
  existing `expense(category_id, effective_date)` index and the chain read its primary key.
- The input digest of a command without a carry confirmation is unchanged (format 1), so a
  receipt stored before version 7 still answers its replay.

## Derived on read, not cached

Carry is replayed by `SignedCarryoverPolicy` on every read, from rows read in one
transaction: the category's chains, its whole default history, and its own month limits
and expenses over only the range the call needs (SELLO-038 consumer boundary, "history
needed"). A snapshot reads the chain covering the month, from its start through the
month before; a drop reads the chain covering this month through this month; a limit
check or correction reads the chains holding the months it touches; the history and the
catch-up notice read every chain they show. There is no cache, so no stale
cache needs fencing: a read is of exactly one revision, and the next read reads again.
Cost is one indexed range read per carrying category, linear in the months and expenses of that range
(a decade of 100 expenses a month is about 12.000 rows, paged by 500). Categories without a
chain cost one primary-key lookup in a command and nothing in a snapshot. A cache can be
added later if a measurement asks for it; it would need the profile revision as its key.

## Commands (`CategoryCommands`, `ExpenseCommands`)

All keep the existing protocol: operation ID, generation, the category's or expense's
expected version, one transaction with the revision and a receipt, replay by digest, and
every refusal before the first write. A carry setting change raises the category's version.

| Command | Carry behaviour | Refusal |
| --- | --- | --- |
| `EnableCarry` | New open chain from the current month, carry-in 0 (CP2–CP4) | `CarryRefused(CarryEnableRefusal)` (CP3, CP25) |
| `DisableCarry(confirmedCarryIn)` | Ends the chain covering this month in it, or removes it if it started now (CP10) | `NotCarrying`; `DropUnconfirmed(DropPreview)` while `K ≠ 0` and not exactly confirmed (CP9) |
| `ArchiveCategory(confirmedCarryIn)` | Same drop, with the existing archive (CP11) | `DropUnconfirmed` |
| `SetDefaultLimit(limit, confirmedCarryIn)` | Unlimited: same drop. Finite: within the cap while a chain is open | `DropUnconfirmed`, `ChainBaseAboveLimit` (CP25) |
| `SetMonthLimit(…, confirmedCarryIn, confirmation)` | This month unlimited: same drop. A past chain month: finite only, a correction (CP20–CP22) | `ChainMonthUnlimited` (CP13), `ChainBaseAboveLimit`, `CorrectionUnconfirmed(CorrectionConfirmation, CarryCorrection)` |
| Expense create, edit, delete, restore (`confirmation`) | A change reaching a closed chain month that changes later months is a correction (CP21) | `CorrectionUnconfirmed(CorrectionConfirmation, CarryCorrection)` |

- **The preview fence (SC34).** A correction is refused with the list of
  `CarryoverCorrectionReplay.preview` and a `CorrectionConfirmation(revision, change)`: the
  revision the list was read at and `change`, the input digest of the command without its
  confirmation. Saving sends the same command with that confirmation. It is accepted only by
  the same change at the same revision: a different command reusing it, or anything
  committed meanwhile, is refused again with its own list (review round 1, R2). The list and the saved figures come from the
  same calculation in the same transaction. This month's own changes, and changes dated
  before every chain, need no confirmation (SC31, SC33).
- **Bounds (CP23).** A change after which any month up to now cannot be calculated is
  refused with `CarryUnavailable(failure)`; nothing is clipped. Only the state *after* the
  change is judged (review round 1, R1, orchestrator ruling): when the stored chain itself
  cannot be calculated (a damaged row, or catch-up recording into it), a correction that
  makes it calculable again goes through without a list, since none can be shown, so the
  category is never locked. Dropping a balance nobody can calculate stays refused with
  `CarryUnavailable` (CP9); SELLO-041 shows that refusal with a pointer to the repair.
- **Unarchive** and a return from unlimited never turn carry on (CP11).
- **Catch-up** records payments in earlier chain months without a preview (CP18) and is
  unchanged: it records due payments even when the chain can then not be calculated (ruling
  on R1); the reads then fail typed (CP8) until a correction repairs it. `CarryoverReads.catchUpNotice` derives the notice from the listed payments.
- **Restore and reset** delete every chain with the rest of the financial rows (CP26,
  SC45); the backup format does not carry chains yet, so a restore leaves every category off.

## Reads

- `MonthlySnapshots` (`RoomMonthlySnapshots`) passes each carrying category's input to
  `MonthlyBudgetPolicy.snapshot` and the chains to `ReservationPolicy.reserved`. Without a
  chain the snapshot is exactly the no-carry one. Failures are typed (CP8):
  `SnapshotFailure.Carryover`, `Unrepresentable` or `Storage`, never base-only figures.
- `CarryoverReads.history(category, through)` (`RoomCarryoverReads`): the chains and
  `CarryReplay` with the generation, revision and financial day (`asOf`) they were read at
  (CP27 provenance).
- `CarryoverReads.catchUpNotice(expenseIds)`: one `CarryCorrection` per category the
  listed, still recorded payments reach (CP18, SC37, SC47), with its revision and `asOf`.

Not done here, for SELLO-041: Resumen and category-detail snapshots still read base limits
only; `:app` maps the new rejections through its existing `else` branches; no composition
binding exists for `CarryoverReads`.

## Portable format impact (SELLO-040)

- `carry_chain` is a new kind of record: category, start month, optional end month. The
  backup must add it in a new format version; versions 1–5 read as no chains (CP26).
- Restore must insert chains after categories and validate before staging: no overlap,
  end after start, a finite base of at most 999.999.999.999 in every chain month and, for
  an open chain, a finite default in force after its last month (SC44). The domain's
  `SignedCarryoverPolicy.replay` and `enable` hold these checks.
- Restore and reset already delete chains; SELLO-040 adds writing them back.
- Room entities are not serialized; no carry amount enters the file (CP26).

## Domain additions (scope note)

Commands and refusals are domain ports, so SELLO-039 added to `:domain`, types only, no
rules: `OperationKind.EnableCarry`/`DisableCarry`; `EnableCarry`, `DisableCarry` and the
optional confirmation fields on `ArchiveCategory`, `SetDefaultLimit`, `SetMonthLimit` and
the four expense commands (defaults keep every caller unchanged); the new
`CategoryRejection` and `ExpenseRejection` cases; and the `CarryoverReads` port.
