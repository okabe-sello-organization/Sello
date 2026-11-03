# SELLO-161 — Reset and restore finish cleanly, off the main thread, and never undo each other

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
An erase that didn't finish can't later wipe a restore's preferences or block new
erases, a restore never reports complete while preferences are unapplied, file errors
are typed failures, and the start-up restore reads its file off the main thread.

### Acceptance criteria
- An erase commits, then its preference clear-out or compaction fails; the person then
  restores a backup. On every later start the restored theme, order and investment
  settings stay, `restored.json` is kept until applied, and a new erase is accepted.
- Compaction whose WAL checkpoint reports busy counts as not compacted: the marker
  stays and the step is retried later.
- An I/O error while reading or writing the restore folder becomes a typed failure,
  never a crash; a failed move after the commit is never reported as a complete restore.
- Restoring from the start-up failure screen reads, parses and validates the file off
  the main thread, once.
- Real-Room or JVM tests for each, including process death between steps.

### Notes
Audit findings: `RoomResets.kt:84-88, 134-147, 233`, `ResetSession.kt:53-61`,
`RoomRestores.kt:286-290, 668-669` (`io {}` catches nothing), `Startup.kt:148, 154`.
ARCHITECTURE §5 and §7, the [recovery matrix](../../testing/recovery-matrix.md).
Review: adversarial (backup, restore, recovery).
