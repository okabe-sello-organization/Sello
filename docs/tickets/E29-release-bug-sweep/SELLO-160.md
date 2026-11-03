# SELLO-160 — Stamps, return order and goal positions follow the rules

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
A stamp earned inside a multi-line receipt is celebrated, investments sort by return as
documented, and a goal's next recording position never wraps.

### Acceptance criteria
- Target 1.000.000, saved 400.000. Aportar varios with an aporte of 200.000 (1 Oct) and
  a retiro of 150.000 (2 Oct): the 50 % stamp is earned, celebrated once, and then shown
  faded ("sigue siendo tuyo"); saved ends at 450.000.
- Sorting by return: Gana, then even, then Pierde, then by tenths. IN16's −1 peso
  ("−0,0 %", Pierde) sorts below an even 0,0 %, and +0,04 % (Gana) above it.
- A goal at `next_sequence` = 9223372036854775807 refuses a target change or movement
  with the typed counter error and writes nothing (closes #275); red-first real-Room test.

### Notes
Audit findings: `GoalPolicy.kt:203-208` with `RoomGoalCommands.kt:559-561` (end state
only); `InvestmentOrderPolicy.kt:51-57`; #275's two `nextSequence + 1` lines in
`RoomGoalCommands.kt`. Goal rules 4 and 5 in [goal examples](../../testing/goal-examples.md).
Review: adversarial (money rules, storage).

### Progress
AC 1–2 delivered by #335; AC 3 (#275) by #330 (SELLO-162), moved by the orchestrator.
AC 3 (#275) delivered by #330 (SELLO-162).
