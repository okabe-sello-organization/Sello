# SELLO-148 — Pagos fijos counts what was really recorded

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-145
- **Gate:** G2

### Outcome
"Ya se anotaron" and the month's bar in Pagos fijos sum the entries each schedule
recorded this month, as they are stored now, not the schedule's current terms.

### Acceptance criteria
- RE17: rent monthly on 5, 950.000, amount changed to 1.000.000 on 10 Oct. October's
  "Ya se anotaron" shows 950.000.
- RE18: on 10 Oct day 5 becomes day 25. October shows 950.000 recorded (5 Oct).
- The 5 Oct entry edited to 900.000 counts 900.000; deleted, it counts nothing and the
  schedule leaves "Ya se anotaron" for October.
- Sums are checked `Money`; tests use hand-worked literals.

### Notes
Owner decision: count what is stored now. Audit finding: `ScheduleListPolicy.kt:98-107`.
Review: adversarial (money rules).
