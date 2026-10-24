# SELLO-093 — Show one goal on Recibo

Recibo shows the goal storage names (`GoalBoard.featured`: the person's choice, else the fallback) as the v2 `GoalPill` under the month's answer, and another active goal at 100 % as the green `ReachedRow` with Marcar como lograda. Both come from `ReciboGoalViewModel` / `ReciboGoal` in `feature/goals`, drawn in Recibo's new `goal` slot by `ReciboDestination`, and are hidden on a past month and with no active goal.
The pill opens the goal and its + opens Aportar; a stamp earned from there lands on the same ticket as on Metas (`StampNoticeBar`); Marcar reuses `GoalCompleter` and `LogradaMoment`. `GoalPill` and `ReachedRow` now follow v2; the row stacks where it does not fit on one line.
Still open: sandbox scenarios and catalog examples for the pill's new description and the row's stacked layout (SELLO-094).
