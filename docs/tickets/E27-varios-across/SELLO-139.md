# SELLO-139 — Save movements of several goals together, or none

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage record up to 30 aportes and retiros spread over several goals as
one operation: every line of every goal is written in one transaction with one receipt,
or nothing is.

### Acceptance criteria
- One command with one operation ID, the generation, and 1–30 lines (goal, kind, amount,
  date, note), with the version read for each goal it names. A committed receipt returns
  its receipt, the new movement IDs in line order, and each goal's change.
- Each goal's lines are checked against that goal's history and its own earlier lines in
  the receipt, as SELLO-128 does for one goal. A refused retiro names its line, its goal
  and the amount that goal had available. A future date names its line.
- Any refused line, a missing, completed or changed goal, zero lines or more than 30
  refuse the whole receipt and write nothing.
- Milestones and reaching 100 % come from each goal's state after the whole receipt.
  Nothing celebrates on a refusal.
- Replay, conflicting replay, an unknown outcome, a stale generation and cancellation
  behave as in SELLO-127. Receipts already stored by SELLO-128's command stay readable.
- No schema change. If one turns out to be needed, stop and ask. The backup format check
  of ARCHITECTURE §7 is done and its result stated in the pull request.

### Notes
Real-Room device tests prove atomicity across goals (a refused line of goal B leaves goal
A untouched) and that a retiro checks only against its own goal. Whether SELLO-128's
command is widened or replaced is the planner's call; no dormant command is left behind.
