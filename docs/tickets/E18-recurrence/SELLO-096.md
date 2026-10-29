# SELLO-096 — Approve recurrence rules and worked examples

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-095, SELLO-004
- **Gate:** G1

### Outcome
Reviewed rules and independent examples for schedules, catch-up, reservations, pause and edits.

### Deliverables
Examples for each schedule kind (month ends, twice a month vs every 15 days), catch-up after weeks away, reservations in Recibo (Sin margen, Revisar), expected income, pause, end and future-only edits.

### Acceptance criteria
- Every ADR 0017 recurrence decision has worked examples with exact dates and amounts.
- Catch-up bounds and the notice's content are defined.
- The rules the [gap list](../../design/v2/gaps/recurrence.md) left open are settled with examples: a deleted occurrence is never recorded again; a manual entry is never matched to a schedule; which two days twice a month means; whether catch-up also runs when the financial day changes while Sello is open; what a schedule does when its category is archived; which occurrences count as reserved `Q` and expected `F` (after today, within the selected current month).
- The owner approves; open points are listed.

### Tests
Owner review; G1.

### Working checkpoint
Approved examples exist and later tickets cite them.

### Context and starting points
[ADR 0017](../../decisions/0017-goals-recurrence-investments.md); ADR 0012 rows; ARCHITECTURE §8.

### Implementation plan
1. Draft.
2. Review.
3. Record approval.

### Concrete cases and pitfalls
- Retain the calendar anchor when clamping month ends.
- v1.1 caught up from a daily worker for up to 60 days; ADR 0017 records on opening with no background work, so the bound is decided here, not copied.
- BG1: while payments are reserved, Revisar comes before Excedido.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-096 --retain`.

### Execution progress

- **Owner decisions, 2026-10-24:** twice a month uses two days the person picks, each
  clamped like monthly, both recorded when they clamp to one date; catch-up runs on
  start, on return and when the financial day changes while open; it records every
  missed occurrence with no age limit; archiving a category pauses its payment schedules
  and unarchiving does not resume them.
- [Recurrence rules and worked examples](../../testing/recurrence-examples.md): 15 rules
  and 40 examples (RE01–RE40), each figure checked by hand and with a throwaway
  calculation outside the repository; seven further points are marked Proposed there and
  approved by merging. Architecture §8 now names the origin's slot and drops WorkManager.
