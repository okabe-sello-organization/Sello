# SELLO-168 — The walking-slice journey waits for the saved note

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
`WalkingSliceJourneyTest` no longer fails when the saved expense's row appears before
its note.

### Acceptance criteria
- The cause is found and fixed, not retried or weakened; if it is the app (the note
  missing from the row's semantics for a moment), the app is fixed.
- The test passes repeatedly on a new API-30 emulator at CI's 412 × 914 dp.
- Closes #319.

### Notes
`WalkingSliceJourneyTest.kt:169`; failed once in CI run 41583780253. Device work only on
a new emulator. Review: lighter.
