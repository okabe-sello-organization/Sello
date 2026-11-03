# SELLO-159 — Movement forms: drafts after 0, after saving, and a readable heading

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-157
- **Gate:** G1

### Outcome
The goal and investment movement forms keep clean drafts and draw the type heading in the
theme's ink.

### Acceptance criteria
- Investment movement in pesos: 0 then 000 then 5 shows and stores "5"; after process
  death the draft comes back as 5, not a refused paste.
- After a confirmed save the goal and investment movement forms clear their draft, so
  a process death before the pop can't bring the same amount back ready to save.
- The type heading uses the theme's ink in all four themes; screenshot baselines
  updated, Oscuro before and after in the pull request (closes #286).
- JVM tests for the drafts.

### Notes
Audit findings: `InvestmentMovementViewModel.kt:226-227` (TripleZero without the
zero guard of `InvestmentMovementsViewModel.kt:257`), `GoalMovementViewModel.kt:541-562`,
`InvestmentMovementViewModel.kt:792-812`; #286 at `InvestmentMovementScreen.kt:270-272`.
Review: lighter, with screenshots.
