# SELLO-128 — Save several movements of one goal together, or none

- **Type:** Story
- **Priority:** P1
- **Status:** Ready
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage can record up to 30 aportes and retiros for one goal as one
operation: every line is written in one transaction with one receipt, or nothing is.

### Acceptance criteria
- One command with one operation ID, the generation, the goal and the version read, and
  1–30 lines (kind, amount, date, note). A committed receipt returns its receipt, the new
  movement IDs in line order, and one change for the whole receipt.
- The lines are checked as if applied after the recorded history in date order, ties in
  line order. A retiro that would take the saved amount below zero refuses the receipt
  and names that line and the amount available. A future date names its line.
- Any refused line refuses the whole receipt and writes nothing. Zero lines or more than
  30 are refused. A changed goal version is refused.
- Milestones and reaching 100 % come from the state after the whole receipt, so the
  screen celebrates once. Nothing celebrates on a refusal.
- Replay, conflicting replay, an unknown outcome, a stale generation and cancellation
  behave as in SELLO-127.
- No schema change. If one turns out to be needed, stop and ask. The backup format check
  of ARCHITECTURE §7 is done and its result stated in the pull request.

### Notes
Each line has its own date because a goal's receipt is mostly used to catch up past
months (orchestrator's call, shown in SELLO-126). Real-Room device tests prove atomicity
and that a retiro checks against the lines before it in the same receipt.
