# SELLO-098 — Store schedules and record due occurrences on opening

Schema 5: `schedule`, `schedule_pause` and unique entry origins; `ScheduleReads`, `ScheduleCommands` (create, change, pause, resume, delete; archiving pauses) and catch-up.
`CatchUpSchedules` + `RoomScheduleCatchUp` record due occurrences in batches of 50; `CatchUpRunner` (`feature/recurrence`) runs on start, day change and every commit, never in the background.
Refusals `CatchUpPending` and `SchedulesPending` show as "not saved" until SELLO-100 words them; the runner's `state` is what SELLO-103's notice builds on.
Backups do not carry schedules or origins yet (SELLO-099); the sandbox still says recurrence does not exist (SELLO-104).
Known flaky, not fixed: `DataStoreUserPreferencesPersistenceTest` reopens a DataStore file without `cancelAndJoin()` (see Execution progress).
