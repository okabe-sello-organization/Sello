# SELLO-127 — Save several expenses or incomes together, or none

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage can record up to 30 expenses, or up to 30 incomes, as one
operation: every line is written in one transaction with one receipt, or nothing is.

### Acceptance criteria
- One command for expenses and one for incomes, each with one operation ID, the
  generation, one date for the receipt and 1–30 lines (category or source, amount, note).
  A committed receipt returns its receipt and the new record IDs in line order.
- The records are ordinary expenses and incomes: lists, totals, editing, deleting and
  exports treat them like single entries.
- Any refused line refuses the whole receipt and writes nothing. The rejection names each
  refused line by position and reason (archived or missing category, future or invalid
  date, an amount the month's totals cannot hold). Zero lines or more than 30 are refused.
- Replaying the same ID with the same input returns the same receipt and IDs. The same ID
  with different input is refused. An unknown outcome is found with the same ID. A stale
  generation, from a reset or restore, is refused.
- A receipt dated in a closed month of a carry chain that changes later months asks for
  one confirmation covering the whole receipt (ADR 0020 CP20–CP22). Confirming sends the
  same receipt again.
- Cancellation is rethrown. A failure inside the transaction leaves no line behind.
- No schema change. If one turns out to be needed, stop and ask. The backup format check
  of ARCHITECTURE §7 is done and its result stated in the pull request.

### Notes
`CatchUpSchedules` is the precedent for a receipt without a single subject. Real-Room
device tests prove atomicity (a fault after line k leaves no line), replay, conflicting
replay and recovery after a restart. Overflow uses hand-worked literals.
