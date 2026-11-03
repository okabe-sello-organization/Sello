# SELLO-143 — Star up to five goals for Recibo

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
The domain and storage keep up to five starred goals instead of one, and answer
Recibo's goals as one ranked list of up to five.

### Acceptance criteria
- Starring keeps up to 5 active goals. Starring a sixth is refused with a typed error
  naming the limit; nothing is written. Starring an already starred goal stays
  refused as `Unchanged`, as today.
- A new unstar command removes one goal's star; unstarring a goal that isn't starred
  is refused as `Unchanged`.
- A replace command unstars one starred goal and stars another in one transaction, or
  does neither. It is refused, writing nothing, when the goal to drop is no longer
  starred, or the new goal is already starred, completed or deleted.
- Star, unstar and replace are goal commands like `FeatureGoal`: an operation ID, the
  generation, a receipt so `find` resolves an unknown outcome, safe replay, and
  cancellation rethrown.
- Completing a goal removes its star in the same write; Deshacer puts it back in the
  same write when fewer than 5 goals are starred, otherwise the goal comes back
  unstarred. A star never sits on a completed goal, so all 5 are always visible.
- Recibo's list: starred active goals first, then the rest, up to 5 in total; inside
  each group by exact completion (saved ÷ target), the earlier-created on a tie. A
  completed or deleted goal leaves the list and loses its star. The single-goal
  answer (`GoalPolicy.featured`, `GoalBoard.featured`) is replaced, not kept beside it.
  The board exposes the starred goals separately from the ranked list, so a goal
  shown only to fill a place does not read as starred.
- Schema change from the one-row `featured_goal` (Room 7 → 8): a migration keeps
  today's starred goal starred (dropped if that goal is completed), the exported schema
  is updated, and a real-Room test shows old data survives. No destructive fallback.
- Backup format moves to version 7 with a list of starred goals. Versions 2 to 6
  restore with their single starred goal (dropped if that goal is completed); a v7
  file with more than 5 stars, a repeated goal, or a star on an unknown or completed
  goal is refused as malformed, never trimmed. `docs/data/backup-format.md` is updated.
- JVM tests cover the ranking with hand-worked literals: ties on rounded percentage
  (57.4 % before 57.1 %), equal completion broken by creation, more than 5 starred
  impossible, fewer than 5 starred topped up, completed goals out, no active goal.

### Notes
Needs the adversarial review (storage, schema, backup); the owner delegated the merge
for this run. Real-Room device tests prove the replace is atomic and the migration
keeps the star. Whether stars stay a table or become a goal column is the planner's
call; state it in the pull request.
