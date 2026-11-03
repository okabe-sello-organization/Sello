# SELLO-156 — Recibo's small slips: delete warnings, archived lists and the dock

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-155
- **Gate:** G1

### Outcome
A delete problem stays with the category it happened in, an archived category's list
offers no Anotar, and the dock never silently does nothing.

### Acceptance criteria
- A swipe-delete on category A ends Changed, Failed or Unknown; opening category B or
  another month shows no message from A.
- The full expense list of an archived category shows the dock disabled with
  "Archivada", as its detail does.
- When the categories can't be read, the dock and `sello://anotar` open the entry form,
  which shows its own load error and retry.
- JVM tests for each.

### Notes
Audit findings: `CategoryDetailViewModel.kt:105, 149-157, 206` (`deleteProblem`),
`ReciboDestinations.kt:155-159`, `ShellViewModel.kt:186-194` (`routeEntry`). The dock
call is the orchestrator's (epic Decisions). Review: lighter.
