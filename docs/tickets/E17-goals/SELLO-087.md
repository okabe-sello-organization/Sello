# SELLO-087 — Store goals and their movements

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-086, SELLO-004
- **Gate:** G2

### Outcome
Goals and their contributions and withdrawals live in Room with exact money, operation IDs and corrections.

### Deliverables
Domain models and policies, a schema migration with exported schema, commands with receipts and undo, reads with typed failures.

### Acceptance criteria
- Migration from the current schema retains every existing row; migration tests prove it.
- Commands are atomic, idempotent by operation ID and reject a withdrawal above saved.
- Corrections and undo recompute progress and stamp state exactly as the approved examples.

### Tests
Real-Room tests for commands, migration, duplicates, stale versions and cancellation; domain example tests; G2.

### Working checkpoint
A goal history can be written and read back with the approved figures.

### Context and starting points
ARCHITECTURE §4–5; approved goal examples (SELLO-086).

### Implementation plan
1. Domain models and policies.
2. Schema, migration and DAOs.
3. Commands, reads and tests.

### Concrete cases and pitfalls
- Stamps earned are history, not a recomputed boolean: keep when they were earned.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-087 --retain`.

### Execution progress

- **Plan approved by the owner, 2026-10-23:** three pull requests: the domain policy,
  schema 4, then commands and reads. Stamps are worked out from the recorded history,
  not stored; movements take an optional 60-character note; goal names need not be
  unique.
- **Part 1, domain:** `Goal`, `GoalMovement`, `GoalTargetChange`, `GoalName` (1–40),
  `GoalId`, `GoalMovementId` and `GoalPolicy` (progress, stamp changes, dates, Recibo's
  goal, pace). `GoalPolicyTest` follows the approved examples by ID; ten deliberate
  breaks of the policy each failed at least one of them. MT39 was added to the examples
  (a later aporte does not cover an earlier withdrawal once its aporte is deleted).
- **Part 2, schema 4:** `goal`, `goal_event` (targets and movements in one recording
  order, unique per goal), `deleted_goal_event` and `featured_goal`, with no cascade;
  migration 3→4 adds them empty and keeps every row; schema exported; strict decoding
  in `GoalMapper`. `GoalStorageTest` reads a stored history back to MT07 and MT17.
  Breaking the migration or the mapper on purpose failed the tests. No command writes
  goals yet, and backup, restore and erase-all do not know them (SELLO-088); no screen
  can create one.
- **Part 3, commands and reads:** `GoalCommands` (`RoomGoalCommands`) with the
  `goal.*` receipts and `GoalReads` (`RoomGoalReads`); `FinancialStorage` offers both,
  and nothing composes them yet. `GoalCommandContractTest` runs MT01, MT07, MT08, MT13–
  MT15, MT17, MT18, MT21, MT23–MT28 and MT30 through the real commands, with replay,
  conflict, stale generation and version, the shared six-second undo and a cancelled
  caller recovering its outcome. Four deliberate breaks of the commands were each
  caught.
