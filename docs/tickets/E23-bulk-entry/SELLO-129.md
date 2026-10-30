# SELLO-129 — Save several movements of one investment together, or none

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage can record up to 30 movements for one investment as one
operation: every line is written in one transaction with one receipt, or nothing is.

### Acceptance criteria
- One command with one operation ID, the generation, the investment and the version read,
  and 1–30 lines (kind, amount in the investment's currency, date, note). A committed
  receipt returns its receipt and the new movement IDs in line order.
- The lines are checked as if applied after the recorded history in date order, ties in
  line order. Every single-movement rule applies with the line named: before the first
  contribution, a retiro above the value on its date, a retiro with no value, a future date.
- Any refused line refuses the whole receipt and writes nothing. Zero lines or more than
  30 are refused. A changed investment version is refused.
- A USD investment's lines are stored in cents of dollars; the receipt never converts
  them at entry.
- Replay, conflicting replay, an unknown outcome, a stale generation and cancellation
  behave as in SELLO-127.
- No schema change. If one turns out to be needed, stop and ask. The backup format check
  of ARCHITECTURE §7 is done and its result stated in the pull request.

### Notes
Real-Room device tests prove atomicity and that a line checks against the lines before
it in the same receipt (for example a valor actual then a retiro on a later date).
