# SELLO-130 — A bulk receipt every entry screen can use

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-126
- **Gate:** G2

### Outcome
`:design-system` has the bulk receipt of the approved design: lines, the highlighted
line, the running total and line removal, ready for the four bulk screens.

### Acceptance criteria
- Draws as the approved SELLO-126 page in Claro and Oscuro: each line's circle, name and
  amount, the highlighted line, a line outlined in loss with its reason, the caption above
  the receipt and the total pinned at the bottom of the slip.
- After five lines the receipt scrolls inside its slip and the total stays in place; the
  receipt never takes more than 30 lines.
- A line is removed by swiping it left past 40 % or from its long-press menu, and the last
  line cannot be removed. Tapping a line highlights it.
- TalkBack reads each line as one item (name, amount, missing reason) and offers removing
  it as an action. The receipt keeps one layout at every font size.
- It takes render models only: no domain types, no money formatting of its own.
- JVM screenshot tests cover the states above in both themes.

### Notes
Built in `:design-system` from the start because four features use it (SELLO-131 to
SELLO-134); that is the orchestrator's call. Reuse `SelloPad`, the chips and the slip
shapes that exist.
