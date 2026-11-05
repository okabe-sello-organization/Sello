# SELLO-172 — Restore and erase say what really happens, and a previewed backup expires on time

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Restore and erase messages tell the truth about the file and the settings, and the
plaintext copy of a previewed backup never outlives its day.

### Acceptance criteria
- On the start-up failure screen, a FinanceTracker export is no longer told "No es un
  respaldo de Sello ni de FinanceTracker". It says it's a FinanceTracker file that is
  imported from Respaldo after Empezar de cero. A file that is neither still gets
  today's message.
- A FinanceTracker file whose Sello translation passes the size or record limits is
  refused as too large (or too many records), not as damaged.
- The erase screen and its "pending" slip name every setting the erase resets,
  including the Inversiones currency and order.
- A staged backup preview older than a day is deleted when Sello next starts, even if
  Respaldo is never opened again. A newer one is kept.
- Red-first tests at the cheapest layer, JVM where possible: the start-up rejection, the
  translation's size refusal with small `BackupBounds`, and the expiry with a
  controlled clock.

### Notes
Audit findings B1, B2, B9 and B4 (2026-11-04): `StorageModule.kt:153-168`,
`RestoreSlips.kt:218`, `strings.xml:957`, `RoomRestores.kt:162-167, 239-245`,
`RestoreSession.kt:~92`, `ResetScreen.kt:257-262`, `strings.xml:889, 910-912`,
`DataStoreUserPreferences.kt:174`. Privacy text: `docs/privacy/mvp-data-handling.md`
already promises the day. The format and schema don't change. Review: adversarial
(backup, restore and recovery).
