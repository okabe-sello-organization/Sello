# SELLO-098 — Store schedules and record due occurrences on opening

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-097, SELLO-004
- **Gate:** G2

### Outcome
Schedules live in Room, and opening Sello records every due occurrence once, atomically.

### Deliverables
Schema migration (schema 5), schedule commands, the catch-up use case with unique (schedule, date) origins, bounded and resumable, and receipts. The origin is a new field on `expense` and `income` and their `deleted_*` tables, which have none today ([gap list](../../design/v2/gaps/recurrence.md)).

### Acceptance criteria
- An occurrence is recorded exactly once, even after a crash or a second opening.
- A recorded occurrence that was deleted is not recorded again.
- Catch-up is bounded and resumes where it stopped.
- Edits apply to future occurrences only; recorded ones stay.

### Tests
Real-Room tests for idempotency, crash between commits, migration and stale versions; G2.

### Working checkpoint
Opening after two weeks records each missed occurrence on its own date.

### Context and starting points
ARCHITECTURE §8 recurrence rules.

### Implementation plan
1. Schema.
2. Commands.
3. Catch-up and tests.

### Concrete cases and pitfalls
- Never write without a unique origin; a retried catch-up must not duplicate.
- Deleting or undoing an occurrence keeps its origin, so catch-up still sees it as handled.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-098 --retain`.

### Execution progress

Delivered in three pull requests, as approved by the owner on 2026-10-24: schema 5 and
reads; commands; catch-up and its triggers.

- **Part 1, schema 5 and reads:** `schedule` and `schedule_pause` tables, and the origin
  (`origin_schedule_id`, `origin_date`, `origin_slot`) of an automatically recorded
  expense or income, unique, carried through delete and undo, with `UndoConflict` when
  another entry already records it. Migration 4→5 keeps every row; `5.json` exported.
  `ScheduleReads` reads every schedule with its pauses and checkpoint, failing on any
  damaged column. Erase-all and restore remove schedules (version 2 backups carry none;
  SELLO-099 adds them). Tests: `ScheduleStorageTest` (11), the 4→5 upgrade in
  `ExportedSchemaOnDeviceTest`, schedules in the erase and restore contracts; seven
  deliberate breaks each fail a test. Merged as #120.
- **Part 2, commands:** `ScheduleCommands` creates, changes, pauses, resumes and deletes
  a schedule with operation identifiers, versions, receipts and replays like every
  command. A new schedule starts today or later; a new rule or start after today, with
  pauses before it dropped; a payment stays a payment. Change, pause and delete wait
  while catch-up has an occurrence due (`CatchUpPending`); deleting keeps every entry
  the schedule recorded. Archiving a category pauses its payment schedules from that
  day, and waits (`SchedulesPending`) while one has an unrecorded occurrence.
  `SchedulePolicy.due` gives what catch-up has still to record. Tests:
  `ScheduleCommandContractTest` (13, RE17, RE18, RE21, RE23), three `due` examples
  (RE10, RE11, two slots); eight deliberate breaks each fail a test. Merged as #121.
- **Part 3, catch-up:** `CatchUpSchedules` (domain) records every occurrence due up to
  today, with no age limit, in batches of 50; a committed batch stays when a later one
  fails. `RoomScheduleCatchUp` writes one batch per transaction: the oldest due across
  schedules (date, creation order, slot), each entry with its origin, the moved
  checkpoints, one revision and a `schedule.catch_up` receipt; it reads first and
  takes the write lock only when something is due. `CatchUpRunner` runs it on start,
  on each new financial day and after each committed change, one run at a time, with no
  background work. Tests: `ScheduleCatchUpContractTest` (11: RE06, RE10–RE13, RE15,
  RE16, RE21, RE36, batches across schedules, archived category), `CatchUpSchedulesTest`
  (5), `CatchUpRunnerTest` (5, RE14) and `CatchUpOnOpeningTest`, the working checkpoint
  on the debug app's real graph; nine deliberate breaks each fail a test. The sandbox
  test harness now ends a graph as a process would (`endProcess`), waiting for its
  background work before the next one opens the file.
- **Unrelated finding, 2026-10-24, logged at the owner's request:**
  `DataStoreUserPreferencesPersistenceTest.preferencesSurviveReopeningTheActualFile` (data
  device suite, from SELLO-023) is flaky. It cancels the first DataStore's scope with
  `cancel()` and reopens the same file at once, so the reopen sometimes meets the old
  instance: "There are multiple DataStores active for the same file". It failed the
  hosted check of #121 once and passed on a full rerun. The fix is `cancelAndJoin()`;
  not changed here, as it is outside this ticket.
