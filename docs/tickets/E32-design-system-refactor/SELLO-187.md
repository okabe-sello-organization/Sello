# SELLO-187 — Each component means the same everywhere

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-184, SELLO-185, SELLO-188
- **Gate:** G2

### Outcome
Buttons, inks, stamps, icons and chips are used for what they mean on every screen.

### Acceptance criteria
- Fixes the misuses left after SELLO-184 and SELLO-185, with the owner's answers in the
  epic's Decisions (M07-3, M08-1, M09 with a new withdrawal icon, M10, M11-3, M12-5,
  M12-6, M12-7, and M12-3 and M12-4 in amber/warn text like `Carry.kt:333` and
  `CategoryCarry.kt:342`) and every sure misuse (M02, M04, M05, M06, the rest of M07, M10–M12).
- The owner sees the new withdrawal icon in the pull request before it merges.
- Re-recorded baselines; the findings page lists no misuse.
