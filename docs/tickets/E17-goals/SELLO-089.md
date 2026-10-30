# SELLO-089 — Add the Metas tab and goal detail

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-087, SELLO-004
- **Gate:** G2

### Outcome
Metas is a main tab with the v2 goal cards, totals band and completed goals, and each goal opens its detail.

### Deliverables
The Metas screen, goal detail and the tab, with loading, empty, failed and adaptive states.
Reuses `GoalsBandTotal`, `GoalCard`, `MilestoneRow`, `CompletedGoalRow`, `GoalPinPill`, `MovementRow`, `ColumnsChart`, `DangerSlip` and `ConfirmSlip` ([gap list](../../design/v2/gaps/metas.md)); extends the shell's `SelloDock` with a tone and icon for the green **Aportar a una meta** / **Crear una meta**, and `CompletedGoalRow` with the LOGRADA landing.

### Acceptance criteria
- Cards ordered by highest completion; completed goals at the bottom with LOGRADA.
- The band totals active goals only, never investments.
- On this tab the dock reads **Aportar a una meta**.

### Tests
Screen and journey tests across windows, text sizes and themes; G2.

### Working checkpoint
Goals appear in Metas and open their detail.

### Context and starting points
v2 Metas and goal pages.

### Implementation plan
1. Tab and navigation.
2. Metas screen.
3. Goal detail and states.

### Concrete cases and pitfalls
- No month bar on Metas: goals are not tied to a month.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-089 --retain`.

### Execution progress

- **Owner decisions, 2026-10-24:** the **+**, **Crear una meta**, **Aportar a una / esta
  meta**, the edit pencil and **Retirar dinero** are built into the screens but left out
  of the running app until SELLO-090 and SELLO-091 add the screens they open; until
  then the dock's label on Metas is proven by screen tests. The star and deleting a
  completed goal are included. "+ … en octubre" adds this month's aportes of active
  goals, withdrawals not subtracted, and is left out when there are none; completed
  goals are listed newest completion first; "Desde" is the day the goal was created;
  Aportes por mes shows up to six months ending this month (or the completion month),
  never before the goal's first month; on wide windows the detail opens as its own
  screen with the rail.
- `GoalPolicy` adds which event earned each stamp, Metas' order, the band's totals
  (exact, an overflow fails the read), what is still missing and aportes per month.
  `GoalReads.observeBoard` reads the board again after every committed change, with a
  failure keeping the last good board.
- `SelloDock` takes a tone and an icon (green flag on Metas, on the dock and the rail);
  `CompletedGoalRow` can land its LOGRADA without a haptic; `GoalsBandTotal` leaves out
  its second line without aportes and relies on the ribbon's margins.
- Metas tab (`MetasRoute`) and goal detail (`GoalRoute`) with loading, failed, stale,
  empty and missing states; no month bar, month switcher, past-month ribbon or Anotar.
