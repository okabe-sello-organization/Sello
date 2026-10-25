# SELLO-103 — Tell what Sello recorded on opening

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-098, SELLO-114, SELLO-004
- **Gate:** G2

### Outcome
After catching up, Sello lists what it recorded so each can be checked, edited or deleted.

### Deliverables
A notice and a list of the occurrences recorded in that catch-up, each opening its edit, built to the design approved in SELLO-114.

### Acceptance criteria
- The notice and list match SELLO-114's approved pages.
- Shown once per catch-up that recorded something; never empty.
- Each item opens the recorded expense or income (`ExpenseEditRoute`, `IncomeEditRoute`).

### Tests
ViewModel and journey tests; G2.

### Working checkpoint
Opening after a week shows what was recorded.

### Context and starting points
[ADR 0017](../../../decisions/0017-goals-recurrence-investments.md).

### Implementation plan
1. Notice.
2. List.
3. Tests.

### Concrete cases and pitfalls
- No notifications or background work.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-103 --retain`.

### Execution progress

- **Notice:** derived from the entries themselves, not a log: every expense and income
  with an origin created after the last **Listo** (`ScheduleRecordReads`, one
  transaction), so one deleted meanwhile leaves the list and nothing new is stored but
  the moment of Listo, a preference on the audit clock that stamps the entries
  (`CatchUpNoticePreferences`). Owner's choice of 2026-10-25: bar and list, Listo
  dismisses, it stays across openings until then; this reads "shown once per catch-up"
  as once per batch of unreviewed entries, however many openings that spans.
- **Bar:** "Sello anotó N pagos fijos / ingresos fijos / pagos e ingresos fijos · Ver"
  above Anotar on Recibo, under a correction notice when there is one; absent when the
  list is empty or cannot be read.
- **List:** *Lo que Sello anotó*, each entry named by note, else category or source,
  with its date and amount, opening `ExpenseEditRoute` or `IncomeEditRoute`; Listo on
  the dock, back keeps the bar; loading, failed with Reintentar, and empty states.
- **Wording:** the intro says "desde la última vez que lo revisaste" instead of the
  design's "desde la última vez que abriste Sello", since the list runs from the last
  Listo, not the last opening.

