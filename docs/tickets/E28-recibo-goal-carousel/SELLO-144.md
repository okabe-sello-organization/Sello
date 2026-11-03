# SELLO-144 — Swipe through goals on Recibo

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-143
- **Gate:** G2

### Outcome
Recibo's goal pill becomes a looping carousel of up to five goals, and a goal can be
starred even when five already are, by choosing which one it replaces.

### Acceptance criteria
- Before building, the owner approves a mock of the carousel (pill and dots) and the
  replace sheet; the [v2 design](../../design/v2/README.md#recibo--home) and its Goal
  detail star pill text are updated to match ("Only one goal can be on Recibo" goes).
- Recibo shows SELLO-143's list in the goal slot, same height as today. No active goal:
  nothing, as now. One goal: today's pill exactly, no swipe, no dots. Two to five:
  swiping sideways moves one goal at a time and loops from the last to the first and
  back; dots under the pill show the place.
- Each pill keeps today's progress fill, icon, name and exact percentage, its round
  **+** (Aportar for that goal), or **✓** at 100 % not yet lograda. Tapping a pill
  opens that goal.
- Recibo opens on the first goal every time, including after a save, a list change or
  returning from a goal. TalkBack reads each pill with its place ("Meta 2 de 4") and
  offers next and previous without a swipe.
- The green "Llegó al 100 %" row shows only for a reached goal that is not in the
  carousel.
- On a goal's detail, the star pill shows whether the goal is starred (not merely
  shown on Recibo to fill a place), and stars or unstars it; its wording is settled in
  the mock. With 5 starred, starring opens
  a bottom sheet listing the 5 starred goals (icon, name, percentage) to pick one to
  replace; picking saves through SELLO-143's replace, backing out changes nothing. A
  refused or unknown save says so and keeps the sheet's choice.
- A failed read keeps the last good goals marked as older, as the pill does today;
  loading, busy and the restored draft of the sheet are handled.
- ViewModel JVM tests cover 0, 1, 2 and 5 goals, ranking order on screen, the sixth
  star, replace success, refusal, unknown outcome and back out. Screenshots show 1
  goal, 3 goals on the second pill, a 100 % pill and the replace sheet.

### Notes
Lighter review by a separate agent, with screenshots. Whether the carousel is a
`HorizontalPager` with a large virtual page count or another way to loop is the
planner's call; it must not rebuild the whole Recibo on each swipe.
