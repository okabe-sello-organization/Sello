# SELLO-178 — Screenshots cover every screen and state the pages list as gaps

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-177
- **Gate:** G1

### Outcome
Every screen and state the design pages describe has a real screenshot, light and
dark, so no page shows a gap.

### Acceptance criteria
- Each gap listed on the design pages has a JVM screenshot test in light and dark at the
  default 412x891 dp size.
- The pages link the new screenshots and list no gaps.
- `verifyRoborazziDebug` passes for both modules.
