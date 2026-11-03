# SELLO-149 — An expense or Varios receipt with an unresolved save can't be closed or sent again

- **Type:** Task
- **Priority:** P0
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
While a save or delete's outcome is unknown, the expense form and Anotar varios keep the
operation and only offer to check it again, so nothing is recorded or deleted twice.

### Acceptance criteria
- Expense and Anotar varios: after an unknown outcome whose lookup also fails, X, system
  Back and Descartar leave the form open; "Comprobar de nuevo" asks with the same
  operation ID. Same behaviour and tests as income's
  `anUnknownSaveCannotBeDiscardedAndLoseTheOperationNeededForRecovery`.
- Deleting an expense with an unknown outcome locks the form; checking again uses the
  same operation ID kept in `SavedStateHandle`. Found committed: the six-second
  Deshacer is offered. Found not committed: the delete can be sent again.
- Red-first JVM tests for each, including after process death.

### Notes
Audit findings: `ExpenseEntryViewModel.kt:458-466`, `BulkEntryViewModel.kt:405-413`,
`ExpenseEntryViewModel.kt:540-568`, `ExpenseEntryState.kt:170`, `Corrections.kt:477`.
Income routes its delete through `Submission` with `STEP_DELETE`. Review: lighter.
