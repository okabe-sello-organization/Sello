# SELLO-191 — The design system has no leftovers and its state is tagged

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-184, SELLO-185, SELLO-186, SELLO-187, SELLO-188, SELLO-189, SELLO-190
- **Gate:** G2

### Outcome
The design system's names, comments and pages match the code, nothing unused is left,
and the result is tagged.

### Acceptance criteria
- RepeatSheet has a general name and ButtonPair leaves Recurrence.kt; the unused category
  palette, semantic.dividend and BUSY_ALPHA are deleted; stale or missing KDoc fixed.
- The findings page says there are no findings; HANDOFF has the E32 entry; the epic is
  Done.
- After it merges, the merge commit on main is tagged `design-system-v1`.
