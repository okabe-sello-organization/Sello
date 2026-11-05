# SELLO-176 — JVM screenshots show the Galaxy S25 Ultra screen

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** None
- **Gate:** G1

### Outcome
Every JVM screenshot of a whole screen is drawn at 412x891 dp, mdpi, the size of a
Samsung Galaxy S25 Ultra, so the design pages show what the owner's phone shows.

### Acceptance criteria
- The default Robolectric qualifiers of `:app` and `:design-system` are
  `w412dp-h891dp-mdpi`.
- No screenshot test pins its own whole-screen size; tests that sweep several windows
  on purpose keep their sizes.
- Desk-width component captures are 412 dp wide.
- Every recorded screenshot is re-recorded and `verifyRoborazziDebug` passes for both
  modules; no other JVM test is weakened, skipped or deleted to pass at the new size.
- ADR 0022 and the quality gates page record the new size.
