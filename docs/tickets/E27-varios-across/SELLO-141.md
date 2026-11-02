# SELLO-141 — Aportar varios across several goals

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-139
- **Gate:** G2

### Outcome
On Aportar varios each line picks its goal, so the person catches up several goals on
one receipt, and can start it from the Metas list.

### Acceptance criteria
- Under the highlighted line, chips name every active goal (not completed), in two rows
  as Anotar varios shows categories. A line's title is its goal, with Aportar or Retirar
  and the note under it.
- From a goal (its movements' foot, or the peeking slip on Aportar/Retirar) line one
  starts in that goal with what was typed, as now. A new line copies the goal, type and
  date above it.
- The Metas list offers **Anotar varios** at its foot, opening the receipt with no goal
  chosen; hidden when there is no active goal.
- A line with an amount and no goal blocks saving like a line with no type (**Falta 1
  meta**). The title reads **Aportar varios** once lines name more than one goal.
- The total is the net (aportes − retiros) of every line. A refused retiro is marked on
  its line with what its goal had available. A goal that changed, was completed or
  deleted meanwhile asks to review and save again; it never overwrites.
- Saved, the receipt closes to where it opened with one confirmation, and each goal that
  reached a stamp or 100 % celebrates once, one after the other.
- Saving, the unknown outcome and the restored draft (with each line's goal) behave as in
  SELLO-133, through SELLO-139's command.
- The design's Anotar varios section drops "one goal" for goals.
- ViewModel JVM tests cover choosing goals, a line without a goal, the list way in, a
  refused retiro in the second goal, a changed goal, celebrations of two goals and every
  save outcome. Screenshots show a receipt with two goals.
