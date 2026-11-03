# SELLO-148 — Pagos fijos counts what was really recorded

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-145
- **Gate:** G2

### Outcome
"Ya se anotaron" and the month's bar in Pagos fijos sum the entries each schedule
recorded this month, as they are stored now, not the schedule's current terms. An entry
counts in the month of its stored date, where Recibo files it, at its stored amount
(ruling on #343).

### Acceptance criteria
- RE17: rent monthly on 5, 950.000, amount changed to 1.000.000 on 10 Oct. October's
  "Ya se anotaron" shows 950.000.
- RE18: on 10 Oct day 5 becomes day 25. October shows 950.000 recorded (5 Oct).
- The 5 Oct entry edited to 900.000 counts 900.000; deleted, it counts nothing and the
  schedule leaves "Ya se anotaron" for October.
- Ruling on #329 (R1): rent ended 30 Sep, its end removed on 20 Oct, so the checkpoint
  jumps to 20 Oct while 5 Oct was never recorded. October shows nothing recorded for it
  (no "Ya se anotaron", no "anotado 5 oct"); a daily café ended 10 Oct and extended on
  20 Oct counts nothing for 11–20 Oct.
- The row's "anotado" date comes from the stored entries too, not the checkpoint: the
  latest stored date among that schedule's entries in the month.
- Sums are checked `Money`; tests use hand-worked literals.

### Notes
Owner decision: count what is stored now. Audit finding: `ScheduleListPolicy.kt:98-107`.
Review: adversarial (money rules).
