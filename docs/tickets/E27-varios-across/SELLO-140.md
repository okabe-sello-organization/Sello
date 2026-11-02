# SELLO-140 — Save movements of several investments together, or none

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage record up to 30 movements spread over several investments as one
operation: every line of every investment is written in one transaction with one
receipt, or nothing is.

### Acceptance criteria
- One command with one operation ID, the generation, and 1–30 lines (investment, kind,
  amount in that investment's currency, date, note), with the version read for each
  investment it names. A committed receipt returns its receipt and the new movement IDs
  in line order.
- Each investment's lines are checked against that investment's history and its own
  earlier lines in the receipt, with every single-movement rule of SELLO-129 and the line
  named: before the first contribution, a retiro above the value on its date, a retiro
  with no value, a future date.
- An amount whose currency is not its investment's, any refused line, a missing or
  changed investment, zero lines or more than 30 refuse the whole receipt and write
  nothing.
- USD lines are stored in cents of dollars and COP lines in pesos; nothing is converted
  at entry.
- Replay, conflicting replay, an unknown outcome, a stale generation and cancellation
  behave as in SELLO-127. Receipts already stored by SELLO-129's command stay readable.
- No schema change. If one turns out to be needed, stop and ask. The backup format check
  of ARCHITECTURE §7 is done and its result stated in the pull request.

### Notes
Real-Room device tests prove atomicity across investments (a refused line of one leaves
the other untouched), a COP and a USD investment on one receipt, and that a line checks
only against its own investment. Whether SELLO-129's command is widened or replaced is
the planner's call; no dormant command is left behind.
