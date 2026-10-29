# SELLO-020 — Complete category management and month-specific budget editing

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-013, SELLO-018, SELLO-008, SELLO-004
- **Gate:** G2

### Outcome
Users can maintain names/icons/limits and retire categories while history stays meaningful.

### Deliverables
Real category edit and change-limit flows, explicit target-month display, archive
confirmation with affected counts, historical/archived presentation and restored
draft/version conflict handling. Category long-press actions have accessible equivalents.

### Acceptance criteria
- Current-month changes never mutate prior limits; rename updates presentation
  without moving expenses to another identity. Archived history remains readable.
- Archive removes category from new-entry chips but does not cascade history.
  No destructive aggregate-delete capability is exposed unless separately accepted.
- Implement approved AR01–AR06, explain current/future-month budget consequences
  before archive/unarchive confirmation, and keep archived rows with saved limits or
  expenses visible in relevant periods. Reuse original identity on restore/rename.
- Draft conflict includes reload/retry choice; “Guardar cambios” requires a confirmed
  receipt. Removing the final active category returns Anotar to the honest prerequisite.
- Announce that previous months' limits can be corrected; retain accessible help
  after dismissal. Historical editor names the target month and explains that other
  configured limits/defaults are unchanged. Confirmed save explains refreshed totals.

### Tests
Device rename/change-limit/archive with prior-month reconciliation; stale edit,
last-category, empty/archive states and process restoration tests; G2.
Cover H06 discovery/help before and after dismissal, explicit historical target/scope,
TalkBack/large-font readability, no success on failed/unknown commit, and receipt-backed
completion feedback. Extend catalog examples for applicable informational states.
Run archive/unarchive across month boundaries, preserved current/override limits,
historical rename and zero-expense archived-budget visibility with exact reconciled totals.

### Working checkpoint
Previously recorded expense/detail remains readable after archival; new entries
cannot accidentally select archived categories. Update related catalog examples.

### Context and starting points
Users need to maintain categories without losing source history or rewriting past
budgets. Inputs are actual category ports from SELLO-013, current detail SELLO-018
and approved D04/D05/D07. Read
[category editor](../../design/sello-spec.html#s-catform) and archive/budget example H01.

### Implementation plan
1. Reuse category editor controls but distinguish create/edit/month-limit mode;
   load stable category ID, row version and explicit selected month from navigation.
2. Add rename/icon/change-limit actions with validated commands and durable receipt
   handling. Show which month is being changed before confirmation/submission.
   Add contextual historical-edit announcement plus retained month/editor help using
   resource copy; after confirmed commit identify the updated period. Explain any
   later approved carryover consequences rather than promising unapproved isolation.
3. Implement archive confirmation with real affected counts, preserve historical
   identity/expenses/limits, and refresh eligible-entry chips through observation.
4. Handle stale editor/deleted row by explicit reload/keep-draft choice; save cannot
   silently upsert a replacement identity. Apply approved archived-month budget semantics.
5. Add accessible long-press/menu equivalents and retained detail/history access,
   including the last-active-category path back to creation prerequisites.

### Concrete cases and pitfalls
- Rename C from Café to Comida: historical rows remain attached to C, not matched
  by translated/name text. Archive C does not remove its September expenses.
- October limit 120,000 cannot rewrite September's 100,000; H01 remains 80,000.
- A form selected before archival must fail at the command boundary, not save into
  an ineligible category because its chips were once loaded.

### Verification recipe
Create/run `*CategoryManagementViewModelTest` and real device CategoryManagementJourneyTest;
G2. Verify rename/month-specific limit/archive/stale draft/restart and retained source
history. Archive-only MVP has no implemented or fake destructive aggregate delete.

### Execution progress
2026-10-12: started on branch `sello-020-category-management` after SELLO-019 merged.
The storage commands for every change here already existed (SELLO-013); this ticket
adds no command, schema, dependency or permission.

**Decisions taken with the project owner before any code**

- A name another category already has is announced while it is typed, in the new
  category form as well, not as the error of a save. The button stays off until the
  name changes.
- Changing name, icon and limit together is three separate commands with an honest
  partial result, not a new combined command.
- In the current month the limit applies from this month onwards; there is no "only
  this month" choice for the month in progress.
- Archiving is an action in the form's top bar, with a confirmation; a long press on
  a category in Recibo opens the form.
- The dismissible first-time announcement that a previous month's limit can be
  corrected is deferred to SELLO-023, which owns remembered preferences; a note is on
  that ticket. This ticket ships the help that is always in view.

**Delivered**

- `CategoryManagementViewModel` and the edit mode of the category form: rename, icon,
  the limit of the month it was opened from, conflict with reload or keep-draft,
  archive and unarchive with a confirmation that states the consequences and counts.
- Entry points: "Editar categoría" on a category's detail; a long press or a
  screen-reader action on its cell in Recibo.
- A line on a finished month's detail saying its limit can still be corrected.
- Design system: `CategoryCell` takes actions, offered by long press and by name.

**Choices made while implementing, for review**

- A month that has a limit cannot be left without one from this form; there is no
  command that removes a limit.
- An archived category's limit cannot be changed from the current month on (the
  command refuses it); a finished month's still can. Its name and icon stay editable.
- "Conservar mis cambios" after a conflict reads the category again and keeps the
  draft, so the next save is knowingly over the newer version.
- Archiving from a form with unsaved changes discards them, and the confirmation says so.
- The long press in Recibo offers "Editar categoría" only; archiving is inside the form.
- One existing assertion changed on purpose: a form showing a taken name used to keep
  its button on, and now keeps it off.
- Texts that were not agreed beforehand are listed in the pull request.

**Not delivered here, and where it goes**

- Archive and unarchive across a month boundary in the installed app: that needs
  simulated financial time (SELLO-024). The approved cases AR01 to AR06 are covered
  against a real database by `MonthlyBudgetHistoryTest` since SELLO-013.
- The dismissible announcement: SELLO-023.
