# Financial storage

Rules: [architecture](../../ARCHITECTURE.md) §§4, 5, 7.

One Room database, `sello.db`, in the app's private storage, is the only place
financial facts live. Its schema is version 6; every version is exported to
`data/schemas/com.software.sello.data.local.SelloDatabase/`.

## What is stored

| Table | Holds | Key and order |
| --- | --- | --- |
| `profile` | The one profile row: financial zone, generation, revision | `id` is always 1 |
| `category` | Name, name key, icon key, archived flag, version, audit times | `id`; unique `name_key` |
| `default_limit` | A category's default limit from a month onwards | (`category_id`, `effective_month`) |
| `month_limit` | The limit set for one category in one month | (`category_id`, `month`) |
| `expense` | Category, effective date, sequence, amount, note, version, audit times, and the origin of one a schedule recorded | `id`; unique `sequence`; unique origin; read by (`effective_date`, `sequence`) |
| `income` | Effective date, sequence, amount, source key and name, note, version, audit times, origin | `id`; unique `sequence`; unique origin; read by (`effective_date`, `sequence`) |
| `operation_receipt` | Proof that an operation committed: kind, input digest, generation, revision, subject | `operation_id` |
| `deleted_expense` | What the last deleted expense was, column for column, with the generation and time of the deletion | `deletion_id`, the deletion's operation identifier |
| `deleted_income` | The same for the last deleted income | `deletion_id` |
| `goal` | Name, icon key, optional target month, completion day, the next recording position, version, audit times | `id` |
| `goal_event` | A goal's history: each target (`target`) and movement (`contribution`, `withdrawal`) with amount, date (movements only), the day it was recorded, note, version, audit times | `id`; unique (`goal_id`, `sequence`), the recording order |
| `deleted_goal_event` | What a deleted goal movement was, for its undo | `deletion_id` |
| `featured_goal` | The goal chosen for Recibo, if any | `id` is always 1 |
| `schedule` | A recurring payment (category) or income (source): amount, note, repeat rule, start and end, creation order, the last occurrence catch-up handled, version, audit times | `id`; unique `sequence` |
| `schedule_pause` | One pause of a schedule: its first day and the day it resumed, if it did | (`schedule_id`, `from_date`) |
| `investment` | Name, optional description, icon key, currency (`COP` or `USD`, never changed), the next recording position, version, audit times | `id` |
| `investment_movement` | An investment's history: `contribution`, `withdrawal`, `dividend` or `valuation`, with amount and currency, date, note, version, audit times | `id`; unique (`investment_id`, `sequence`), the recording order; replayed by (`effective_date`, `sequence`) |
| `deleted_investment_movement` | What a deleted investment movement was, for its undo | `deletion_id` |
| `usd_rate` | One entered USD rate: pesos per dollar in whole hundredths (`rate_hundredths`, 4.150,25 is 415025), its date, audit time | `id`; unique `sequence`, the order rates were entered |

There are no widget or capture tables, and no
preference such as theme or category order: those are not financial and stay out
of Room, in the DataStore file `sello-preferences` (`DataStoreUserPreferences`). A
preference that cannot be read falls back to the last good value or the default and
never touches financial data.

- **Generation** starts at 1 and advances only when the whole history is replaced
  (restore or reset). **Revision** starts at 0 and advances with every committed
  change. Both are created with the zone in one insert.
- **Receipts** have no foreign key to the record they describe and hold no amount,
  name or note. That is what lets them, and the profile row, outlive a restore
  without keeping erased content. A reset removes them all and keeps only its own.
- **`deleted_expense` and `deleted_income` are not financial facts.** A deleted
  record is gone from `expense` or `income`, so no read or total can count it; the
  row exists only so the deletion can be
  [undone](commands.md#editing-deleting-and-undo). Across both tables there is at
  most one row. A later deletion of either kind replaces it, and opening storage
  removes it (`FinancialStorage.discardUndoCopies`, called by `openStorage`), because
  the time to undo is counted in the process that deleted. Like a receipt they have no foreign
  key. They are not part of a portable backup (SELLO-028), and a reset erases them
  with the rest.
- A month with neither a `month_limit` nor an applicable `default_limit` is
  unconfigured. That is different from a finite limit of zero and from `unlimited`.

## How values are stored

| Value | Stored as |
| --- | --- |
| Identifier | Lower-case hyphenated UUID text; any other spelling is damage |
| Money | `*_minor` whole minor units plus a `currency` code; only `COP` is valid, except that an investment and its movements may be `USD` (cents) |
| Rate | Whole hundredths of a peso per dollar, an `INTEGER`; never `REAL` |
| Date, month | `yyyy-MM-dd`, `yyyy-MM` text, so text order is calendar order |
| Audit time | Milliseconds since the epoch, from `AuditClock`. A writer truncates to milliseconds first so a value reads back equal |
| Kind, source, icon | Stable lower-case keys (`finite`, `unlimited`, `paused` for a default only, `salary`, `shopping_cart`); never a label or an enum position |
| Zone | The IANA identifier, such as `America/Bogota` |

## Who enforces what

- **The database** refuses: a second category with the same name key, including an
  archived one; a second receipt for an operation; a repeated ordering sequence; an
  expense or limit whose category does not exist; deleting or re-keying a category
  that has expenses or limits. Every foreign key is `RESTRICT`; nothing cascades.
- **The mappers** (`data/mapper`) check every column of every row they read against
  the domain rules and stop at the first that fails, returning
  `StorageFailure.Integrity(table, row, column)`. Nothing is defaulted, trimmed,
  clamped or skipped, and one damaged row fails the whole read. The failure carries
  no stored value, so it is safe to log.
- **Commands** are the only way to change anything: each writes the record, the
  revision and the receipt in one transaction ([commands](commands.md)).
- Room cannot declare `CHECK` constraints. Value ranges are therefore enforced by
  the typed domain values on the way in and the mappers on the way out, not by
  hand-written triggers that the exported schema would not describe.

## Opening and failing

`openFinancialStorage` in `:data` is the only entry point; `:app` sees
`FinancialStorage` and domain ports, never Room. There is no destructive fallback:

- A file from a newer schema with no migration is refused and left untouched.
- A damaged file is refused and left untouched. Android's default is to delete a
  damaged database and start empty; `NonDestructiveOpenHelperFactory` removes that.
- A database error becomes `StorageFailure.Unavailable`. Cancellation is rethrown.

At startup `openStorage` (app `composition`) establishes the profile before the
graph is built: the first start of an installation stores the device zone, and every
later start reads the stored zone and ignores the device's. It then discards what
an earlier process kept to undo a deletion. If either fails the storage is closed
and the failure returned; the app never continues on the device
zone or an empty database, and shows the failed start instead
([time and composition](time-and-composition.md#when-the-data-cannot-be-opened)).

`setAsideFinancialStorage` is the only other thing done to the file: for a closed
database that would not open, it moves `sello.db` and its companion files to
`sello-unreadable.db` in the same folder, so the next open starts an empty history.
Nothing is deleted except an earlier kept copy, and a move that cannot finish is
undone and reported ([ADR 0008](../decisions/0008-unreadable-storage-start-over.md)).

## Backups by the system are off

`allowBackup` is false and both rule files exclude every storage domain from cloud
backup and device transfer, so the database and the preferences never leave the
device through Android. The intended backup is a file the person saves themselves.
Its [format](../data/backup-format.md) is independent of this schema: a schema
change needs no new backup version unless what a backup carries changes. Saving
the file from Configuración → Respaldo writes it through the system document picker;
the last successful backup is remembered in DataStore, never in Room.

Restore (`RoomRestores`) stages a checked file in the profile's private folder
(`sello-restore/`, `sandbox-restore/` for the sandbox), then in one transaction
deletes every financial row and kept undo copy, inserts the backup's rows with
their identifiers and order, sets the backup's zone, advances the generation and
revision, and stores a `history.restore` receipt with no subject. Preferences are
applied afterwards from the staged file, which stays until each group is applied.

Setting an unreadable database aside also clears its restore folder: a staged file
or pending preferences belonged to the store that moved. A restore from the start-up
failure screen then stages into the new store's folder as usual.

Reset (`RoomResets`) writes `pending.properties` (the operation only) to the profile's
`sello-reset/` folder (`sandbox-reset/`), then in one transaction deletes every
financial row, kept undo copy and receipt, sets the device's zone, advances the
generation and revision, and stores a `history.reset` receipt with no subject. After
it, the restore folder and the set-aside `*-unreadable.db` files are deleted, the
database is compacted (`VACUUM`, then the write-ahead log truncated) so no erased row
survives in free pages, and every preference key is cleared in one DataStore edit.
The pending file goes once all of that is done; at start-up a pending file whose
receipt exists is finished, and one without a receipt is forgotten.

## Changing the schema

1. Change the entities, raise `version` in `SelloDatabase` and build: KSP writes the
   next `N.json`. Commit it. Never edit an exported file.
2. Add a `Migration` and register it in `openSelloDatabase`. No destructive or
   automatic fallback.
3. Extend `ExportedSchemaOnDeviceTest` with `runMigrationsAndValidate` from every
   earlier version, using rows that must survive, and update `ExportedSchemaTest`.
4. Review whether the portable backup format is affected.

Goals (SELLO-087) are stored as their history: stamps and percentages are worked out
from it by `GoalPolicy` and never stored ([goal rules](../testing/goal-examples.md)).
Backups (since format version 2), restore and erase-all carry the goal tables with the
rest (SELLO-088). The current backup format version is `BackupCodec.VERSION`.

Schedules (SELLO-098) are stored as their terms; their occurrences are worked out by
`SchedulePolicy` and never stored ([recurrence rules](../testing/recurrence-examples.md)).
An expense or income a schedule records carries its origin, `origin_schedule_id`,
`origin_date` and `origin_slot`: all three or none, unique, kept in the copy for undo,
and with no foreign key, so the entry outlives its schedule. A schedule's
`handled_date` and `handled_slot` are the last occurrence catch-up dealt with; it and
every earlier one are never recorded again, even after their entry was deleted.
Catch-up writes those entries and moves the checkpoints in one transaction per batch,
with a `schedule.catch_up` receipt that has no subject ([commands](commands.md#catch-up)).
Backups (since format version 3, SELLO-099) carry schedules, their pauses and checkpoints
and every entry's origin; restore puts them back as they were and erase-all removes
them.

Investments (SELLO-107) are stored as their history: value, gain and return are worked
out by `InvestmentPolicy` and never stored ([investment rules](../testing/investment-examples.md)).
Every write replays the candidate history first, so a retiro above the value on its date,
a movement before the first aporte or deleting the only aporte is refused with nothing
written. A new investment writes its row, its first aporte and its first value (the next
position, the same date) and, for a first USD investment, the rate in one transaction.
Deleting an investment deletes its movements in that command's transaction, never by a
cascade. Rates are only ever added; the current one is the latest date, then the last
entered. Backups carry investments, their movements and the rates (since format version 4,
SELLO-108); restore puts them back as they were, each movement at its recording position,
and erase-all removes them. Neither keeps a deleted movement's undo copy.

## Not here yet

- Nothing in this list; what is stored where, and what may leave, is in
  [MVP data handling](../privacy/mvp-data-handling.md).
