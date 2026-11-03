# SELLO-E28 — Several goals on Recibo

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person glances at up to five goals on Recibo, swiping through them in the goal pill's place, and adds to any of them without opening Metas.
- **Exit:** Up to five goals can be starred, Recibo's goal pill is a looping carousel of up to five goals ranked by the rule below, and a sixth star asks which starred goal it replaces.

## Decisions

**Owner direction (2026-11-02):** given in chat on the orchestrator's brief.

- Recibo keeps one goal slot of the same height; it never grows into a list. The
  carousel is room for people who want it, not new clutter.
- The carousel shows up to 5 goals, always 5 when 5 are active. Starred active goals
  come first, then active goals that are not starred. Inside each group the higher
  completion comes first, with today's tie rule: exact completion (saved ÷ target, not
  the rounded percentage), then the earlier-created goal
  (`GoalPolicy.featured`, [ADR 0017](../../decisions/0017-goals-recurrence-investments.md)).
  With nothing starred this is today's fallback, extended to a list.
- Up to 5 goals can be starred. Starring a sixth opens a small screen listing the 5
  starred goals to pick one to replace; picking swaps both stars in one save, backing
  out changes nothing.
- Every pill in the carousel keeps today's progress fill and its own round **+**
  (Aportar), or **✓** at 100 % not yet lograda. Tapping a pill opens its goal.
- Swiping loops: past the last goal comes the first again. One goal shows exactly as
  today, with no swipe or dots.
- Recibo opens the carousel on its first goal every time; the position is not kept.
- Orchestrated run; subagents and separate review agents are approved for this epic.

**Orchestrator's calls (2026-11-02), open to the owner:** a bottom sheet for the replace
screen; dots under the pill only with 2 or more goals; TalkBack reads each pill's place
("Meta 2 de 4"); tapping a filled star unstars that goal; completing a goal removes its
star and Deshacer restores it when there is room; the detail star shows "starred", not
"shown on Recibo"; a restored backup with more than 5 stars or a repeated star is
refused, never trimmed. The v2 design has no
carousel or replace sheet, so SELLO-144 shows the owner a mock before building.
