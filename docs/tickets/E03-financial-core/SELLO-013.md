# SELLO-013 — Implement category commands and historical monthly budget storage

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-011, SELLO-012, SELLO-004
- **Gate:** G2

### Outcome
Categories and period limits can evolve without silently changing past financial meaning.

### Deliverables
Typed category create/rename/icon/archive and monthly-limit commands using the
receipt/version/generation protocol; current-month initialization/rollover policy
from SELLO-001, archived-category query rules and atomic readable budget history.

### Acceptance criteria
- Identity survives rename; archival prevents new expenses without deleting history.
  No category color column or translated name used as ID.
- A limit change targets its explicit month; current/future defaults never rewrite
  previous periods. Unlimited versus zero limit is defined, not conflated.
- Historical months resolve defaults effective then, including unopened/skipped months;
  explicit monthly overrides win. Snapshot creation is idempotent/atomic and independent
  of app-open timing; no backward extrapolation before known configuration.
- Approved D07 archives preserve the current monthly limit, exclude automatic budgets
  in following archived months and retain rows in periods with saved limits/expenses.
  Same-month unarchive preserves its limit; later unarchive preserves any explicit
  period override, otherwise uses the last configured default. Rename keeps identity.
- Stale versions/missing rows report conflicts; failed edits do not partially
  mutate budgets or revision. First category creation needs no sample data.

### Tests
Real-Room command races/rollback/missing rows, rename/archive retention, multiple
month rollover, unlimited/zero limits and prior-month stability. Run G2.
Cover approved H03/H04: October default 100,000, unopened November, December default
120,000 → November limit 100,000; backdated November expense 10,000 → remaining
90,000. Reopen/repeated materialization preserves that history and explicit overrides.
Cover AR01–AR06 archive/unarchive/history/override cases on real Room, including
zero-expense saved-budget visibility and no retrospective budget after restoration.

### Working checkpoint
Real category/budget workflows are callable and observed through domain ports;
first-run UI can be implemented without bypassing validated writes.

### Context and starting points
Renaming or changing today's budget must not redefine past spending. Use operation
protocol from SELLO-012, actual schema from SELLO-011 and accepted D04/D05/D07.
Read architecture §§4/5; user workflows consume these ports in SELLO-016/020.

### Implementation plan
1. Define narrow create/edit/archive/limit commands with explicit category ID/month,
   expected version, operation ID and generation. Category color is not a new field.
2. Persist name/icon separately from identity; apply archive/new-entry eligibility
   rules and reject malformed/missing/version-conflicting updates transactionally.
3. Implement accepted month-limit creation/carry-forward/skipped-period behavior,
   preserving historical facts and distinguishing unlimited from numeric zero.
4. Atomically update revision/receipt for category and related limit changes. Return
   domain values/read ports suitable for entry chips and historical detail.
5. Document the port contract and tests so UI cannot implement its own shortcut
   budget writes or silently cascade archived expenses.

### Concrete cases and pitfalls
- Rename category ID C: original expenses still reference C. Archive C: old September
  totals remain; new October expense in C rejects.
- H01 remains exact after October edit. An absent historical limit is not license
  to substitute the current limit; D04 decides the missing-period presentation.
- Repeating an archive operation must follow its receipt protocol; an unrelated
  stale editor is a conflict, not an idempotent success.
- Retain dated financial facts and per-month configured limits needed by MVP;
  future signed carryover must not require reconstructing history from today's
  default. ADR 0002 records that direction, not permission for carryover schema now.

### Verification recipe
Create/run `CategoryCommandContractTest` and `MonthlyBudgetHistoryTest` on real Room,
plus pure budget-policy tests and G2. Test current/past month commands, concurrent
edit/archive and rollback with exact retained IDs/amounts.

### Execution progress
2026-10-10: started on branch `sello-013-category-budget-commands` after SELLO-012
merged. No product decision or dependency was needed and the schema did not change:
the tables are those of SELLO-011, with one new stored kind, `paused`, for a default.

Delivered. `:domain`: the seven category commands and their typed outcomes,
`CategoryReads` with `MonthBudget`, and `CategoryBudgetPolicy`, the one rule for
what a month's limit is. `:data`: `RoomCategoryCommands`, `RoomCategoryReads`, and
the steps shared by every command moved into `CommandProtocol.kt`, which the expense
workflow now uses too. `:app`: both ports bound in the graph; no screen calls them
yet. `docs/development/categories-and-limits.md` states the contract.

Choices made during the work, for owner review:
- No row is created per month. A month's limit is worked out from the history of
  defaults and that month's own limit whenever it is read, so it cannot depend on
  when the app was open. D04 allows this: materialization is "an implementation
  mechanism, not a requirement".
- Archiving records a pause that starts the following month. Unarchiving in the
  same month removes it; unarchiving later restores the last default from that
  month and leaves the gap paused.
- A category and its limits share one version. Changing a limit and renaming at the
  same time from two places is a conflict for the second.
- A default cannot be changed while the category is archived; unarchive first. A
  specific month's limit can still be corrected, and the category renamed.
- A month's own limit can be set for the current or any past month, including one
  before the category existed, because that is an explicit correction. It cannot be
  set for a month that has not started.
- Renaming to the same name with different capitals or accents is allowed.
- Archiving an archived category, or unarchiving one that is not, is refused with
  its own reason instead of counted as success.
- An operation identifier belongs to one kind of command. Reusing an expense's
  identifier for a category change, or the reverse, is a conflict.
- The month read returns archived categories too. Hiding one that has neither a
  limit nor spending in a month needs spending, so it belongs to SELLO-014.

Left for later: observing changes and combining limits with spending (SELLO-014);
the first-run and editing screens (SELLO-016, SELLO-020); removing a month's own
limit, which no screen needs yet; the sandbox scenarios (SELLO-024).
