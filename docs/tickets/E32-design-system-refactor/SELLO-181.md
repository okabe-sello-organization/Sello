# SELLO-181 — One status slip says what kind of news it brings

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
`:design-system` has a `StatusSlip` whose required kind (Empty, LoadFailed, Older,
ActionFailed, Unknown, Gone) decides its icon, ink, layout and button, and the owner has
approved how each kind looks.

### Acceptance criteria
- `StatusSlip` takes a kind, the words and at most one action; callers cannot set ink,
  icon or button kind.
- Older is an inline slip for figures that are still shown; LoadFailed never sits above
  figures; Unknown offers to check; Gone says the thing no longer exists.
- JVM screenshots of every kind in light and dark, plus the busy action; the components
  page shows them. No screen uses it yet.
- The owner approves the six kinds in the pull request before it merges.
