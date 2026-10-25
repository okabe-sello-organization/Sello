# SELLO-094 — Cover goals in the sandbox and catalog

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-092, SELLO-004
- **Gate:** G2

### Outcome
Sandbox scenarios and catalog examples exercise goals through the real command paths.

### Deliverables
Scenarios for stamps, withdrawals, target edits and completion; catalog states for the extensions the earlier tickets add, and device tests for `GoalPill`, `MovementRow`, `StampTicket`, `GoalPinPill` and `IconChoiceStrip`, which have none ([gap list](../../../design/v2/gaps/metas.md)).

### Acceptance criteria
- Scenarios use real commands and storage with synthetic data.
- Catalog shows every extended component in all themes and states.
- None of it ships in the release build.

### Tests
Scenario corpus tests; catalog tests; G2.

### Working checkpoint
The sandbox runs each goal scenario to its check.

### Context and starting points
AGENTS UI and developer-tools rules; ARCHITECTURE §9.

### Implementation plan
1. Scenarios.
2. Catalog.
3. Tests.

### Concrete cases and pitfalls
- Keep sandbox clocks distinct as §9 requires.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-094 --retain`.

### Execution progress

Delivered in two pull requests, as approved by the owner on 2026-10-24: the sandbox's goals,
then the catalog and component device tests.

- **Part 1, the sandbox's goals:** goal writes in the debug build now go through the same
  `WriteFence`, faults and operation log as every other financial write
  (`FencedGoalCommands`, `FaultyGoalCommands`), and goal reads share the sandbox's read
  failure (`FaultyGoalReads`); before, goal writes skipped all three. The diagnostic's
  `OperationInspector` reads goal receipts. Scenarios gain goal steps (create, aporte, retiro,
  target, Marcar, Deshacer, choose for Recibo) and goal checks of saved, percentage, the four
  stamps, completion and Recibo's goal, each from an approved MT example; a restored history
  is checked by goal name. Four scenarios: `metas-sellos`, `metas-retiros`,
  `metas-objetivo`, `metas-lograda`. The corpus test compares goals, movements and
  Recibo's choice between the fresh and resumed runs, and compares only the checks the
  resumed run takes again.
- **Part 2, still open:** catalog examples for the extended goal components, device tests
  for `GoalPill`, `MovementRow`, `StampTicket`, `GoalPinPill` and `IconChoiceStrip`, the
  ticket's evidence, and closing E17.
