# Backup file format, version 4

Rules: [architecture](../../ARCHITECTURE.md) §7, [D05, D08 and D09](../decisions/0001-mvp-contract.md#d08--privacy-and-portable-files),
[ADR 0003](../decisions/0003-mvp-transaction-amount-range.md). Code: `data/.../backup`.

A Sello backup is one **plaintext UTF-8 JSON** file. Anyone who opens it can read
every amount, name and note in it: it has no password, encryption or checksum, and
the app must say so before a person saves one. Its version is the file's own and
changes independently of the Room schema; nothing in it is a Room entity, a table or
column name, a Kotlin class name or an enum position.

File name: `sello-respaldo-AAAA-MM-DD-HHmm.json`, the moment the backup was made in
its financial zone (`BackupFormat.fileName`). MIME type: `application/json`.

## Shape

Sello writes version 4 (SELLO-108) and reads versions 1 to 4. Version 2 is version 1
plus `financial.goals` and `financial.featuredGoal` (SELLO-088); version 3 is version 2
plus `financial.schedules` and each expense's and income's `origin` (SELLO-099);
version 4 is version 3 plus `financial.investments` and `financial.usdRates`. A version 1
file reads as having no goals, versions 1 and 2 as having no schedules and no origins,
and versions 1 to 3 as having no investments and no rates. A backup with none of them
is still version 4, so an app that reads only version 3 cannot read it.

```json
{
  "format": "sello-backup",
  "version": 4,
  "exportedAt": "2026-10-22T14:30:00Z",
  "financial": {
    "sourceRevision": 9,
    "zone": "America/Bogota",
    "categories": [ … ],
    "defaultLimits": [ … ],
    "monthLimits": [ … ],
    "expenses": [ … ],
    "incomes": [ … ],
    "goals": [ … ],
    "featuredGoal": "…" | null,
    "schedules": [ … ],
    "investments": [ … ],
    "usdRates": [ … ]
  },
  "preferences": { … } | null
}
```

[`valid-g01.json`](../../data/src/test/resources/backup/valid-g01.json) is a complete
synthetic version 1 example: G01's three expenses and one income, plus an archived
category. [`valid-g01-v2.json`](../../data/src/test/resources/backup/valid-g01-v2.json),
[`valid-g01-v3.json`](../../data/src/test/resources/backup/valid-g01-v3.json) and
[`valid-g01-v4.json`](../../data/src/test/resources/backup/valid-g01-v4.json) are the
same data in versions 2 to 4, the last as Sello writes it now;
[`valid-goals-v2.json`](../../data/src/test/resources/backup/valid-goals-v2.json) adds one
goal (approved example MT07), and
[`valid-schedules-v3.json`](../../data/src/test/resources/backup/valid-schedules-v3.json)
holds recurring payments and incomes with the entries they recorded (recurrence
examples RE05 and RE10), and
[`valid-investments-v4.json`](../../data/src/test/resources/backup/valid-investments-v4.json)
holds three investments and two USD rates (investment examples IN04, IN05 and IN27, the
rates 4.000,00 and 4.150,25). The `invalid-*.json` files beside them each break one rule.

| Key | Value |
| --- | --- |
| `format` | Always `"sello-backup"`. |
| `version` | `1` to `4`. Any other number is refused as a version this app cannot read. |
| `exportedAt` | When the backup was made, UTC. |
| `financial.sourceRevision` | The revision all financial rows were read at: one transaction, one revision. Informative; a restore does not reuse it. |
| `financial.zone` | The financial zone, an IANA identifier such as `America/Bogota`. |
| `financial.featuredGoal` | Version 2: the `id` of the goal chosen for Recibo, one of `goals`, or `null`. |
| `preferences` | Read separately from the financial rows, after them; they never shared a transaction. `null` when they could not be read: the money is backed up regardless. |

### Records

| List | Keys |
| --- | --- |
| `categories` | `id`, `name`, `icon`, `archived`, `createdAt`, `updatedAt` |
| `defaultLimits` | `categoryId`, `from` (month), `kind`, `amount`, `currency` |
| `monthLimits` | `categoryId`, `month`, `kind`, `amount`, `currency` |
| `expenses` | `id`, `categoryId`, `date`, `sequence`, `amount`, `currency`, `note`, `createdAt`, `updatedAt`, and in version 3 `origin` |
| `incomes` | `id`, `date`, `sequence`, `amount`, `currency`, `source`, `sourceName`, `note`, `createdAt`, `updatedAt`, and in version 3 `origin` |
| `goals` (v2) | `id`, `name`, `icon`, `targetMonth`, `completedOn`, `createdAt`, `updatedAt`, `history` |
| `history` (each goal) | `id`, `sequence`, `kind`, `amount`, `currency`, `date`, `recordedOn`, `note`, `createdAt`, `updatedAt` |
| `origin` (v3, each expense or income) | `schedule`, `date`, `slot`, or `null` for one entered by hand |
| `schedules` (v3) | `id`, `kind`, `categoryId`, `source`, `sourceName`, `amount`, `currency`, `note`, `repeat`, `first`, `second`, `start`, `end`, `sequence`, `handled`, `pauses`, `createdAt`, `updatedAt` |
| `handled` (each schedule) | `date`, `slot`, or `null` before catch-up first dealt with it |
| `pauses` (each schedule) | `from`, `until` (`null` while still paused) |
| `investments` (v4) | `id`, `name`, `description`, `icon`, `currency`, `createdAt`, `updatedAt`, `movements` |
| `movements` (each investment) | `id`, `sequence`, `kind`, `amount`, `currency`, `date`, `note`, `createdAt`, `updatedAt` |
| `usdRates` (v4) | `id`, `rateHundredths`, `date`, `sequence`, `createdAt` |

`preferences` has `theme` (`system`, `light`, `dark`), `ink` (`cobalto`, `violeta`),
`categoryOrder` (`most_used`, `alphabetical`, `amount_spent`, `limit`,
`latest_expense_added`) and `categoryDirection` (`descending`, `ascending`).

Every key is required, even when its value is `null`, and no other key is allowed.

### Values

- **Identifiers:** the original lower-case hyphenated UUIDs. They identify records
  inside the file; a restore gives commands fresh runtime identities and a backup
  ID never authorizes replaying an operation.
- **Amounts:** whole COP pesos as a bare JSON integer, `125000`. Not `"125000"`,
  `1.25e5`, `125000.0` or `0125000`. Each expense or income is 1 to 999.999.999.999;
  a single one outside that range rejects the whole file. Limits may be 0 and are
  not bound by that maximum. Totals across records may exceed it.
- **Currency:** `"COP"` beside every amount; any other code rejects the file, except
  that an investment and its movements may be `"USD"`, whose amounts are whole cents
  (`105050` is USD 1.050,50).
- **Limit kind:** `finite` (with `amount` and `currency`), `unlimited` (both `null`),
  or, for a default only, `paused` (both `null`), which means no automatic budget
  from that month, as after archiving.
- **Dates and months:** `yyyy-MM-dd` and `yyyy-MM`, real calendar values.
- **Moments:** UTC as `Instant.toString` writes them: `2026-10-22T14:30:00Z`, or
  with milliseconds `…:30.250Z`. Finer precision or an offset is refused. `exportedAt`
  is the export's time rounded down to the millisecond: newer Android clocks give finer.
- **Text:** stored form only: names 1–24 code points and unique ignoring case and
  accents, notes up to 60, other-income names up to 24, no surrounding spaces or
  control characters. A note is either text or `null`, never empty.
- **Goals:** a name is 1–40 code points in stored form; `targetMonth` a month or
  `null`; `completedOn` a date or `null`. `history` is the goal's targets and
  movements in recording order: `sequence` strictly increasing (gaps are kept), the
  first entry a `target`. A `target` has `id`, `date`, `note`, `createdAt` and
  `updatedAt` all `null`; a `contribution` or `withdrawal` has them all, with the
  amount rules of an expense. The history must work out with the
  [goal rules](../testing/goal-examples.md): a withdrawal above what was saved at its
  point rejects the file. Stamps and percentages are not in the file; they are worked
  out from the history.
- **Schedules** ([recurrence rules](../testing/recurrence-examples.md)): `kind` is
  `payment`, with a `categoryId` in the file and `source` and `sourceName` `null`, or
  `income`, with `categoryId` `null` and an income `source`. Amount, currency and note
  follow an expense's rules. `repeat` is `monthly` (`first` the day, 1–31),
  `twice_monthly` (`first` and `second` the days, `first` earlier), `every_days` (1–365)
  or `every_weeks` (1–52); `second` is `null` but twice a month. `start` and `end` are
  dates, `end` on or after `start` or `null`; `pauses` begin on or after `start`, are in
  order and do not overlap, and only the last may be open. `sequence` is the creation
  order, unique. `handled` is the last occurrence catch-up dealt with: a date and a slot,
  `2` only twice a month. A payment into an archived category must be paused, as
  archiving leaves it.
- **Investments** ([investment rules](../testing/investment-examples.md)): a name is
  1–40 code points and a description up to 40 or `null`, in stored form, never empty;
  `currency` is `COP` or `USD`. `movements` is the investment's whole history in
  recording order: `sequence` strictly increasing (gaps left by deletions are kept).
  `kind` is `contribution`, `withdrawal`, `dividend` or `valuation` (a recorded value);
  `currency` is the investment's; `amount` is 1 to 999.999.999.999 minor units, and a
  `valuation` may also be 0. The note follows an expense's rules. The history must work
  out with the investment rules: at least one contribution, nothing but a contribution
  dated before the first one, and no withdrawal above the value on its date or before
  any value, or the file is rejected. A movement belongs to the investment it is
  written in; it names no other. Value, gain and return are not in the file.
- **USD rates:** `rateHundredths` is pesos per dollar in whole hundredths, a bare JSON
  integer: `415025` is 4.150,25, from `100` (1,00) to `10000000` (100.000,00). A decimal
  number such as `4150.25`, a rate with three decimals or one outside the bounds rejects
  the file, never rounded. `sequence` is the order rates were entered in, unique; the
  current rate is the latest `date`, then the highest `sequence`.
- **Origins:** `slot` is `1` or `2`; one origin appears at most once among expenses and
  once among incomes. When its schedule is in the file, it is at or before that
  schedule's `handled`, so a restore never records it again; one whose schedule was
  deleted since stays as it is.
- **Order:** `sequence` is kept as written, so history keeps its order. Files are
  written categories by creation then ID, limits by category then month, records
  by date then sequence, goals by creation then ID with each history by sequence,
  schedules by `sequence` with each schedule's pauses by first day, investments by
  creation then ID with each history by sequence, rates by `sequence`; the same data
  always makes the same bytes.

## Not in a backup

Record, category, goal and investment versions, a goal's or investment's next recording
position, the profile generation, operation receipts, the copies kept for undo
(`deleted_expense`, `deleted_income`, `deleted_goal_event`, `deleted_investment_movement`),
a goal's stamps and percentage, an investment's value, gain and return, a schedule's
occurrences (worked out from its terms) and catch-up receipts, jobs, sandbox or debug
state, one-time notice dismissals, permissions, URIs, paths and credentials.

## Reading a file

`BackupCodec.read` writes nothing. It checks in this order and stops at the first
problem, rejecting the whole file with a `BackupRejection` that names where it is
(`financial.expenses[3].amount`), never what the file says there:

1. **Size:** at most 10 MiB; a stream is read only one byte past that.
2. **Structure, before parsing:** UTF-8; nesting at most 8 deep; at most 100,000
   array elements in all; at most 10,000 characters in any string (an escape counts
   as one); no key twice in one object; not cut short.
3. **Header:** `format` and `version`, so a newer file is named as such even when
   the rest of it has changed shape.
4. **Shape:** that version's keys exactly.
5. **Values and links:** every rule above, then duplicate IDs, category names,
   sequences and category/month entries; every `categoryId` must be a category in
   the file; at most 1,200 default-limit entries per category; every investment's
   history worked out with the investment rules. Investments, movements and rates count
   toward the 100,000 array elements like any other record.

The result is a `BackupPlan`: the validated contents and their counts, with every
record and goal at version 1. Restoring it is SELLO-030.

## Making a file

`BackupExport.create` reads every financial row in one Room transaction, then the
preferences, encodes, and reads its own output back with the same rules, so the app
never hands out a file it would refuse. It returns the name, bytes and counts. A
refusal for size (bytes, records or default-limit entries) tells the person their data
is more than a backup holds; any other refusal is a fault in Sello and says so.

In the app (`feature/backup`), the system's "Guardar como" offers the name;
`AndroidDocumentWriter` writes, flushes and closes the chosen document, and only then
is the backup "Guardado" and remembered (`BackupRecords`, in DataStore). A start is
recorded before the first byte, so a process that dies mid-save leaves an unknown
outcome, never a saved one. Sello never deletes a document it failed to complete.

## Restoring a file

In the app (Respaldo → Restaurar), the system's open dialog returns a document; at
most 10 MiB plus one byte is read. `RoomRestores.stage` checks it with
`BackupCodec.read` and keeps it privately with a new operation identifier, the
history position (generation, revision) it was previewed at, and the time. A staged
file survives a restart, but is forgotten after a day or once the generation changes.

The replacement refuses a history that moved since the preview; the person previews
again. Goals and Recibo's choice are replaced with the rest, never merged: each goal's
next recording position follows its last event, and each target gets a new identifier.
Schedules are replaced the same way, each with its pauses and `handled` checkpoint, and
entries keep their `origin`; catch-up then goes on from each checkpoint, so nothing the
backup holds is recorded again and what came due since is. Investments and rates are
replaced too: each movement keeps its recording position, the investment's next position
follows its last movement (at least 2), and no deleted movement can be put back. The
restore preview and the erase confirmation count investments when there are any.
Inside one transaction a receipt for the same operation means it already
happened, so asking again after an unknown outcome or a crash replays instead of
replacing twice. Afterwards the backup's appearance and order are applied; a group
that fails stays pending, with the file, and is retried at start-up and on request.

From the start-up failure screen there is no store to stage into, so the file is
checked whole with `BackupCodec.check` and held in memory for the preview. Confirming
sets the unreadable database aside, opens a new empty store and stages and replaces
there as above.

## Changing the format

Any change to keys, meanings or rules is a new `version`. Versions 1 to 4 stay
readable for as long as the app supports restoring them; nothing translates older Sello
databases or other apps' files.
