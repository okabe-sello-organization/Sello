# SELLO-198 — Keep events with their expenses

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Domain and storage hold events and each expense's optional event, safely across saves,
restores, backups and exports.

### Acceptance criteria
- An event has an ID, a name (1–24 characters, no control or invisible characters,
  unique once folded as category names are) and an icon key from the category icon set.
  An expense has an optional event.
- Creating, renaming and deleting an event, and setting or clearing an expense's event
  (inside today's expense save, edit and Anotar varios commands), carry an operation ID
  and a receipt. They replay safely, check an unknown outcome before any retry, and
  rethrow cancellation.
- Deleting an event clears it from all its expenses in the same transaction and never
  deletes an expense. A rename or delete against a stale version is refused, writing
  nothing.
- Room 9 → 10: a migration adds the table and the column, the exported schema is
  updated, and a real-Room test shows old data survives with no events. No destructive
  fallback.
- Backup format version 8 adds events and each expense's event. Versions 1–7 read as
  having no events. A file naming an unknown event, or two events with one folded name,
  is refused whole. The [backup format](../../data/backup-format.md) page is updated, and
  reset clears events.
- The CSV and JSON export gain the event's name, empty when there is none. A
  FinanceTracker import brings no events.
- Tests cover invalid names, double submit, a stale version, an unknown commit,
  cancellation, a failed read, restart and rollback. Overflow doesn't apply: an event
  stores no money.

### Notes
Adversarial review (storage, schema, backup); the owner merges. Expense commands keep
their current contracts; the event is one more validated field.
