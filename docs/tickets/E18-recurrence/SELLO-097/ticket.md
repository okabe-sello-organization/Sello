# SELLO-097 — Calculate schedule occurrences

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-096, SELLO-004
- **Gate:** G2

### Outcome
Domain works out every occurrence of a schedule, exactly, for any range.

### Deliverables
Schedule models and an occurrence policy for the three kinds, with pause and end.

### Acceptance criteria
- Matches every approved example, including clamping and anchors.
- Every 15 days and twice a month differ as approved.
- Pauses skip occurrences; an end date stops them.

### Tests
Property and example tests in domain; G1 host tests within G2.

### Working checkpoint
Occurrences match the examples.

### Context and starting points
Approved examples (SELLO-096).

### Implementation plan
1. Models.
2. Policy.
3. Tests.

### Concrete cases and pitfalls
- Use financial dates, never device time.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-097 --retain`.

### Execution progress

- **Domain, 2026-10-24:** `Schedule`, `ScheduledEntry`, `RepeatRule` (monthly, twice a
  month, every N days or weeks, each checked), `Recurrence` (start, end, pauses) and
  `Occurrence` (date and slot) in `domain/model/Schedules.kt`, `ScheduleId` beside the
  other identities; `SchedulePolicy.occurrences`, `newStart` and `changedStart` follow
  the [recurrence examples](../../../testing/recurrence-examples.md) (rules 2, 3, 7, 8).
- **Tests:** `SchedulePolicyTest` (21): RE01–RE09 and RE18–RE23 copied from the examples,
  bounds and validation, and 1.500 seeded random schedules checked for order,
  uniqueness, a day-by-day scan, split ranges and limits. Red on a skeleton that returned
  nothing (18 of 21 failing); ten deliberate breaks of the policy each fail a test.
