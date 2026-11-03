# SELLO-169 — The shrunk release build walks every journey added since the MVP

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-145, SELLO-146, SELLO-147, SELLO-148, SELLO-149, SELLO-150, SELLO-151, SELLO-152, SELLO-153, SELLO-154, SELLO-155, SELLO-156, SELLO-157, SELLO-158, SELLO-159, SELLO-160, SELLO-161, SELLO-162, SELLO-163, SELLO-164, SELLO-165, SELLO-166, SELLO-167, SELLO-168
- **Gate:** G3

### Outcome
The R8 release build, signed with a throwaway key on a new emulator, is walked through
every journey shipped since 2026-10-23, so shrinking breaks nothing the person uses.

### Acceptance criteria
- Walked on screen, each against expected figures: goals (aporte, retiro, stamps,
  lograda and Deshacer, the Recibo carousel), investments (COP and USD, rate,
  movements), fixed payments (create, catch-up, pause, change), Anotar varios across
  categories, goals and investments, signed carryover, the FinanceTracker import,
  backup and restore of a history with all of these, erase, and the start-up failure
  screen.
- No crash is logged. Anything that breaks gets its own fix (keep rules included)
  before this ticket closes.
- Results recorded in [the performance profile](../../testing/performance-profile.md)
  as the optimized-release table is today. The pull request refreshes
  [HANDOFF](../../development/HANDOFF.md) and closes the epic.

### Notes
The last walk (2026-10-23) covered the MVP only. Not a release: nothing is published,
signed with the owner's key or tagged. Review: lighter.
