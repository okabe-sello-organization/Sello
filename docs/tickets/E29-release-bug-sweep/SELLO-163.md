# SELLO-163 — Storage and file errors are always reported

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Every database error inside a command becomes a typed outcome, and "Guardado" appears
only after the backup or export file is really written and closed.

### Acceptance criteria
- `runCommand` turns any `android.database.SQLException` into a rejection before the
  commit point and into `OutcomeUnknown` after it, the same split as today; a test with
  an injected `SQLException` that isn't a `SQLiteException`.
- The document writer treats a failed sync on a real file and a peer-reported close
  error as failures; pipes and sockets keep today's behaviour. "Guardado" never shows
  after such a failure.
- Tests at the cheapest layer that proves each.

### Notes
Audit findings: `CommandProtocol.kt:90` catches only `SQLiteException`, while
`reading` (`:138`) and `ObservedReads.kt:39` catch `SQLException`;
`AndroidDocumentWriter.kt:91-101` swallows sync errors (`ParcelFileDescriptor.checkError`).
Review: adversarial (storage, backup).
