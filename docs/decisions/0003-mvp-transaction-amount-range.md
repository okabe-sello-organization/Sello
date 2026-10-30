# 0003 — One MVP transaction amount range

**Owner:** project owner (user) · **Updated:** 2026-10-29
**Status:** approved product rule.

Each expense or income must be **1–999,999,999,999 COP**. Shared domain validation
owns this rule across entry, commands, storage decoding and backup validation.
A UI-only check is insufficient. Preserve valid amounts exactly during edits.

Reject a backup containing an out-of-range transaction before changing data.
Never clip amounts, skip invalid records or report partial restore success.
Malformed stored amounts must produce a typed read failure, never zero, empty
history or destructive repair.

The limit applies to individual transactions and to a category limit when a person
sets one, not to totals. Two valid expenses of 600,000,000,000 total
1,200,000,000,000. Keep exact checked `Long` totals; reject actual overflow, not a
sum above the transaction limit. Expanding the range needs explicit policy approval
and storage/backup compatibility review. This rule does not add currencies or an
account ledger.

**Amended 2026-10-29 (owner):** a category limit is capped at the same maximum when
set; limits already stored or restored from a backup keep reading.

## Verification

Cover 0, 1, the maximum, maximum + 1, oversized paste, exact amounts after note
edits, totals above the transaction cap and aggregate overflow. M05/M06 in the
[financial examples](../testing/mvp-financial-examples.md) define expected cases.
SELLO-028/030 must prove invalid-backup rejection leaves real storage unchanged.
