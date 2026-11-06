# SELLO-199 — Put an expense in an event

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-198
- **Gate:** G1

### Outcome
While recording or correcting an expense, the person picks the event it belongs to, or
creates one on the spot.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/events-mock.html).
- Anotar and the expense editor have a row under the note: "Agregar a un evento" when
  empty, or the event's icon and name with ✕ to remove it. Anotar varios has the row once
  for the receipt, and every line gets that event.
- The row opens a sheet listing events (the one with the most recent expense first, then
  the newest), each with its count and total, then Nuevo evento and Sin evento.
- Nuevo evento asks for a name and an icon. A refused name says why and keeps the text.
  Creating it selects it for the expense.
- A new Anotar starts without an event. The choice is kept in a restored draft, and a
  failed save keeps it on the form.
- An expense recorded by a fixed payment has no event until the person adds one while
  correcting it.
- JVM tests for the form state (set, clear, create, restored draft, failed save).
  Screenshots of both row states, the sheet and Nuevo evento.
