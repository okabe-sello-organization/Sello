# SELLO-011 — Create Room v1, integrity constraints and recovery metadata

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-010, SELLO-005, SELLO-004
- **Gate:** G2

### Outcome
Establish the minimum durable MVP model and migration safety rather than inheriting legacy entities.

### Deliverables
Exported Room v1 schema for profile/financial zone/generation/revision, categories,
monthly limits, expenses/income, operation receipts and actual recovery/compensation
metadata as required. Stable IDs/logical ordering, strict mappers, foreign keys,
unique operation identities, indexes, bounded queries and DB construction/composition.

### Acceptance criteria
- No recurrence, goals, investments, widgets or capture tables. Preferences that
  are nonfinancial remain outside Room; financial configuration stays inside it.
- Unknown stored kind/currency/date is an integrity failure, not a default or empty list.
- Recovery metadata can survive reset/restore financial replacement; generation
  and revision initialize atomically. Destructive fallback is prohibited.
- First-use/upgrade startup is deterministic and keeps financial zone persistent.
- Disable unapproved OS/cloud backup and device transfer before storing the first
  user financial data; SELLO-032 later verifies/hardens that boundary, not first disables it.

### Tests
Real-Room create/reopen, uniqueness, reference and strict-mapper tests; exported
schema validation and initial migration harness. Test malformed persisted rows and
DB initialization races. Run G2 with device evidence, not mocked DAO assertions.

### Working checkpoint
Real DB opens/reopens in the app; the existing UI still launches without seeded
financial records or fake reads. Future schema changes add tested migrations.

### Context and starting points
Room is financial authority, including revisions/recovery, not just CRUD storage.
Inputs are SELLO-005 clocks and SELLO-010 types. Read architecture §5,
[Room/migration sources](../../development/execution-guide.md#source-library) and accepted D04/D09.
Use `data/local` for schema ownership; app sees it only through composition.

### Implementation plan
1. Draft/implement minimum entity relationships: persistent profile zone/generation/
   revision; stable category ID/name/icon/archive/version; month limits; expense/income
   ID/date/sequence/amount/source or category/version/audit; receipts/control recovery rows.
2. Define foreign keys, operation uniqueness, ordered query indexes and schema checks.
   Use stable wire/storage keys and strict date/currency decoding, not translated enums.
3. Implement one real Room database/open factory and domain mappers, plus bounded
   read/transaction access. Export v1 schema and create migration-test configuration.
4. Initialize profile once atomically using the real financial-zone port. Separate
   rows retained for restore/reset recovery from the financial rows being replaced.
5. Disable unapproved OS backup/transfer before real money storage. Add real-Room
   creation/reopen/constraint/integrity tests and document schema/version ownership.

### Concrete cases and pitfalls
- Foreign category reference or duplicate operation origin must be rejected by storage,
  not merely an in-memory Mutex. Unknown persisted kind/date is integrity failure.
- Receipts must not disappear during the operation they recover. Conversely reset
  cannot retain erased user payloads disguised as recovery metadata.
- Do not invent v0 migrations; initial schema is v1. Adding a field in later tickets
  requires updating/exporting a real schema and testing every supported upgrade.

### Verification recipe
Create `DatabaseIntegrityTest`/`ProfileInitializationTest` in data/androidTest and
run `./gradlew :data:connectedDebugAndroidTest`, followed by G2. Inspect exported
schema, merged backup rules and reopened-file results; mocked DAOs are insufficient.

### Execution progress
2026-10-09: started on branch `sello-011-room-v1` after SELLO-009 merged and E02 closed.
No product decision was needed: D04, D05, D07 and D09 are approved and say the schema
is this ticket's choice.

Delivered. `:data`: Room schema version 1 exported to `data/schemas`, with tables
`profile`, `category`, `default_limit`, `month_limit`, `expense`, `income` and
`operation_receipt`; entities, DAOs with bounded ordered reads, strict mappers, the
profile store, and `openFinancialStorage` as the one entry point. `:domain`: record
identifiers, `Category`, `IconKey`, `BudgetLimit`, `DefaultLimit`, `MonthLimit`,
`Expense`, `Income`, `FinancialProfile`, `StorageFailure` and the
`FinancialProfileStore` port. `:app`: startup reads the financial zone from the
database, and system backup and device transfer are switched off.
`docs/development/storage.md` describes the tables, formats and how to change them.

One dependency was added, for tests only: `androidx.room:room-testing` at the Room
version already pinned, which provides the migration harness this ticket requires.

Choices made during the work, for owner review:
- Identifiers are lower-case hyphenated UUID text. Any other spelling is rejected,
  not normalized.
- Limits use two tables: a category's default limit from a month onwards, and the
  limit set for one month, which wins. A month with neither is unconfigured. This
  answers H01 to H04 without creating a row for every month. SELLO-013 owns the
  commands and how archiving pauses a default.
- A category and its limits share the category's version; limit rows have none.
- Each expense and income has a sequence that is unique in its table and decides the
  order of records on the same day.
- A receipt stores a digest of the submitted input, not the input, and has no link
  to the record it describes, so it can outlive a replacement without keeping erased
  amounts, names or notes. SELLO-012 defines the operation kinds and the digest.
- Room cannot declare value checks, so ranges are enforced by the domain types on
  the way in and the mappers on the way out. No hand-written triggers.
- Android deletes a damaged database file and starts an empty one by default. That
  is removed: the damaged file is kept and the open fails.
- If the database cannot be read at startup the app stops instead of continuing on
  the device zone or an empty database. Showing that as a screen belongs to the app
  shell (SELLO-015); today it is a crash with a clear cause.
- Startup waits for one small read of the profile row, because the financial zone
  must be known before any date is computed.
- An icon key is any well-formed lower-case key; which keys have a drawing is the
  app's concern, so a file from a newer version stays readable.

Left for later, each with its owner: the meaning of receipts and their lookup
(SELLO-012); limit and category commands (SELLO-013); deletion and undo records
(SELLO-019), which will add their own schema version; restore and reset, which
advance the generation and remove old receipts (SELLO-030, SELLO-031). The first
later schema change adds the first real migration and its upgrade test; none was
invented here.

2026-10-09: owner approved pull request 9, including the choices above; moved to Done
for a squash merge.
