# Commands and receipts

**Owner:** SELLO-012 · Rules: [architecture](../../ARCHITECTURE.md) §5.

How a change to financial data is made so that its result is always knowable.
Expenses (`ExpenseCommands` in `:domain`, `RoomExpenseCommands` in `:data`), income,
[categories and limits](categories-and-limits.md) and goals all follow these steps.

The shared steps are functions in `data/repository/CommandProtocol.kt`; each workflow
keeps its own typed command, checks and outcome. There is no generic repository.

## What a caller does

1. Pick an `OperationId` (from `RecordIdSource`) **before** submitting and keep it
   with the draft, so it survives rotation and process death.
2. Submit the command with that identifier and the `generation` it was prepared against.
3. Act on the outcome:

| Outcome | Meaning | Next |
| --- | --- | --- |
| `Committed(expense, replayed)` | Saved. `replayed` is true when this operation had already committed and this call wrote nothing | Read the record back, say it was saved and close. Confirm a new save only when `replayed` is false |
| `Rejected(reason)` | Certainly not saved; the reason is typed | Show the reason; the draft is still valid to correct |
| `OutcomeUnknown(operationId)` | The commit itself failed in a way that does not say whether it took effect | Call `find(operationId)` or submit the **same** command again. Never a new identifier |
| a `CancellationException` | The caller was cancelled. It says nothing about the save | On return, `find(operationId)` |

`find` returns the committed result, `null` when the operation did not commit, or a
`StorageFailure`. It never guesses.

## What the transaction does, in order

1. Read the profile. A different `generation` is `StaleGeneration`: the history was
   replaced, so even an operation that once committed is refused.
2. Look for a receipt with this operation identifier. Same kind and input: return
   the original result. Anything else, including the identifier of a different kind
   of command: `OperationConflict`.
3. Validate against stored state: the category exists and is not archived, and the
   date is not after the financial day.
4. Advance the revision by one, insert the record, insert the receipt.

Every rejection returns before the first write. A database error during these steps
rolls everything back and is `Rejected(StorageFailed)`. Only an error from the
commit itself is `OutcomeUnknown`.

The revision, an expense's ordering sequence and a category's version advance through
`nextCounter`. It never wraps, saturates or starts again: at the largest value it
raises `CounterExhausted`, which rolls the transaction back and is
`Rejected(StorageFailed(Integrity(table, row, counter)))`, never `OutcomeUnknown`.
Typing cannot reach that value; a damaged or imported database can. Only that
signal is caught: any other arithmetic fault inside a command is a bug and is left to
fail as one.

## Editing, deleting and undo

`EditExpense`, `DeleteExpense` and `RestoreExpense` follow the same steps
with their own kinds, `expense.edit`, `expense.delete` and `expense.restore`, and
their own input identities. Edit and delete also carry the expense `version` the
caller read. Step 3 for each:

| Command | Refused with | Writes |
| --- | --- | --- |
| Edit | `ExpenseMissing`; `VersionConflict(current)`; `Unchanged` when nothing differs; `CategoryMissing` or `CategoryArchived` only when moving to another category; `FutureDate` or `InvalidDate` only when changing the date | The same row: new category, date, amount and note, version + 1, `updated_at`. Identity, sequence and `created_at` never change |
| Delete | `ExpenseMissing`; `VersionConflict(current)` | Removes the row and keeps it, column for column, in `deleted_expense`, replacing any earlier kept copy |
| Restore | `UndoExpired`; `UndoConflict` | Puts the kept row back exactly as it was, same version and timestamps, and removes the kept copy |

Each is one revision and one receipt whose subject is the expense. The row update or
removal is guarded by the version in SQL as well, and the transaction fails if it
does not change exactly one row.

- **An expense may stay in a category archived since**, with its other fields edited.
  It cannot be moved into an archived category.
- **The date rule is applied only when the date changes.** An expense already dated
  after today, which needs the financial day to have moved backwards, can still have
  its amount, note or category corrected; it cannot be moved to another future date.
- **A new expense never takes the sequence of one that can still come back**: the
  next sequence follows the highest of `expense` and `deleted_expense`.

### The six seconds

`ExpenseCommands.undoOffer()` is the one deletion that can be undone, with the
`MonotonicClock` reading at which it stops being so: `UndoPolicy.WINDOW`, six
seconds, after the deletion committed. At six seconds exactly it has ended.

- The offer is held in memory by `UndoWindow`, of which storage has one, shared by
  the expense and income workflows.
  Nothing a screen does (rotation, leaving and returning) touches it, and a replay
  of the deletion does not start it again. `FinancialClock` and `AuditClock` are
  never consulted, so simulated financial time cannot shorten or extend it.
- **A process that ends takes the offer with it.** The next one has none, restore
  answers `UndoExpired`, and startup removes the kept copy. The deletion's receipt
  is still found with `find`.
- **A second deletion makes the first final.** Restore naming the first is
  `UndoExpired`.
- **Restore is refused with `UndoConflict`** when the kept copy belongs to another
  generation (the history was replaced), or the expense's identifier or sequence is
  in use again. It does not ask whether the category was archived meanwhile: the
  expense goes back where it was.
- **A restore that already committed is a replay** whatever the time is, in this
  process or a later one, so recovering its outcome never deletes or restores twice.
- Delete and restore run one at a time and, like every transaction, are not stopped
  by a cancelled caller: a deletion that committed always leaves its offer, even
  when the caller never heard the result.
- When a deletion ends as `OutcomeUnknown` no offer is made. If `find` then shows
  that it committed, it stands.

## Income

`IncomeCommands` and `RoomIncomeCommands` record, edit, delete and restore
income by exactly the steps above, with their own kinds (`income.create`,
`income.edit`, `income.delete`, `income.restore`), input identities and typed
rejections. An income has an amount, a source, a date and an optional note; it has no
category.

- **Nothing is shared with expenses but the steps.** Income has its own table, its own
  ordering sequence and its own kept copy. No income command reads or writes an
  expense, a category or a limit, and a month's budget figures never include income.
  A receipt of one kind is not found by the other's `find`.
- **The source is a stable key** (`salary`, `freelance`, `passive_income`, `transfer`,
  `other`). Only `other` carries a name, which is free text beside the key and never
  becomes a key. Both are part of the input identity, so the same operation with
  another source is a conflict.
- **The date rules are the expense's:** not after the financial day when recording, and
  checked on an edit only when the edit changes it.
- **There is one offer to undo, not one per kind.** `UndoWindow` holds it for both
  workflows. Deleting an income makes an earlier deletion of an expense final and the
  other way round, and each workflow's `undoOffer()` answers only for its own kind.
  Everything under [the six seconds](commands.md#the-six-seconds) holds for income as written.

`IncomeReads` lists a month's income newest first in bounded pages, reads one income,
and gives a month's exact total and count at one revision (`month`, and `observeMonth`
after every committed change). A month without income is a total of zero; an income
that cannot be read fails the month, it is never left out.

## Goals

`GoalCommands` and `RoomGoalCommands` (SELLO-087) follow the same steps with the `goal.*`
kinds: create, edit, change the target, record, edit and delete a movement, put a
deleted movement back, complete, undo a completion, delete, and choose Recibo's goal.
The receipt's subject is always the goal. The rules are the approved
[goal examples](../testing/goal-examples.md).

- **Every change to a goal's history is checked before it is written.** The history
  with the change applied is worked out by `GoalPolicy`; a withdrawal above what was
  saved at its point, or a correction that would leave less than zero, is refused as
  `AboveSaved` naming the withdrawal (MT08, MT15, MT39). So a stored history always
  works out.
- **Every write raises the goal's version,** so a command prepared on an older read of
  the goal is a `VersionConflict`. A completed goal refuses everything but deletion.
- **A deleted movement shares the one offer to undo** with expenses and income, with
  its copy in `deleted_goal_event`.
- **Deshacer after a completion** is offered in memory for the time the command asks
  (about ten seconds, longer with a screen reader), only while nothing has changed the
  goal since, and never in a new process: then the completion is final.
- **A committed aporte, retiro or target change says which stamps it earned or lit
  again** (`CommittedGoal.change`) for the celebration. A replay, a correction or a
  deletion says nothing, so nothing is celebrated twice.

`GoalReads` reads every goal with its progress, and Recibo's goal after the fallback
rule, at one revision; one goal that cannot be read fails the read.

## Schedules

`ScheduleCommands` and `RoomScheduleCommands` (SELLO-098) follow the same steps with the
`schedule.*` kinds: create, change, pause, resume and delete. The receipt's subject is
the schedule; every write raises its version. The rules are the approved
[recurrence examples](../testing/recurrence-examples.md).

- **No command records an occurrence.** Catch-up alone does, so a command that would
  change what it still has to record (change, pause, delete) is refused with
  `CatchUpPending` while an occurrence up to today is due and unrecorded.
- **Dates:** a new schedule starts today or later; a new rule or start, after today,
  and pauses before it no longer apply. The entry and the end alone keep the rule and
  its anchor. An end before today is refused. A payment stays a payment.
- **Pause and resume** write `schedule_pause` rows: a pause from today (or the start),
  ended on the day it is resumed, which counts again. Resumed the day it began, it
  leaves nothing.
- **Deleting** removes the schedule and its pauses; every entry it recorded stays.
- **Archiving a category** pauses each of its payment schedules from that day, in the
  same transaction, and is refused with `SchedulesPending` while one has an
  unrecorded occurrence, so nothing is ever recorded into an archived category.

## Guarantees and how they are kept

- **One operation, one record.** The receipt's primary key is the operation
  identifier, and the check and the insert share a transaction, so two simultaneous
  submissions cannot both write.
- **A cancelled caller cannot leave half a write.** The caller is checked before the
  transaction starts; once started, the transaction always runs to its end, and the
  caller is checked again afterwards. A caller cancelled meanwhile gets the
  cancellation while the receipt is already durable.
- **Input identity.** `inputDigest` is a SHA-256 over the meaningful fields, each
  written as byte length, colon, bytes. For an expense: kind, format version,
  generation, category, amount, currency, date, note-present flag, note. The operation identifier is not
  part of it. A label, a formatted amount or an object's text form never is.
- **Time.** Rows and receipts are stamped by `AuditClock`. `FinancialClock` is asked
  only which day it is, so simulated time can never appear in a timestamp.
- **No side work in the transaction.** No file, preference, network or notification
  call happens inside it. A later failure of any such follow-up cannot turn a saved
  expense into "not saved"; the receipt already exists.

## Not here yet

- No totals are checked when saving. With each record at most 999.999.999.999 COP, a
  month would need over nine million records to exceed what a total can hold, far
  beyond the 100.000-record backup bound. Totals are checked where they are
  computed.
- `Committed` has no follow-up state because nothing follows a save yet.
