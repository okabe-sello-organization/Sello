# SELLO-150 — "Este y los siguientes" stays one change after a failed save

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-149
- **Gate:** G1

### Outcome
When a scheduled payment's correction saved the schedule but not the entry, changing the
terms again asks the question again, so the entry and the schedule never silently differ.

### Acceptance criteria
- Correct a recorded 50.000 to 52.000 with Este y los siguientes: the schedule takes
  52.000, the entry is refused. Change it to 55.000 and save: Solo este / Este y los
  siguientes is asked again. Este y los siguientes sends the schedule 55.000, then the
  entry 55.000; Solo este saves the entry at 55.000 and the schedule stays 52.000.
- Saving the same 52.000 again still only retries the entry
  (`whenOnlyTheFollowingChangedSavingAgainCorrectsThisOneWithoutResending` stays green).
- The same for incomes. Red-first JVM tests for expense and income, including after
  process death.

### Notes
Owner decision: ask again. Audit findings: `ExpenseEntryViewModel.kt:581-590, 676`,
`IncomeFormViewModel.kt:442-451, 520` (`KEY_FOLLOWING`, `KEY_FOLLOWING_SAVED`).
Review: lighter.
