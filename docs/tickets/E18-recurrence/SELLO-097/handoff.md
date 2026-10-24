# SELLO-097 — Calculate schedule occurrences

`domain/model/Schedules.kt` holds `Schedule`, `ScheduledEntry`, `RepeatRule`, `Recurrence` (start, end, pauses) and `Occurrence` (date, slot); `ScheduleId` is in `RecordIds.kt`.
`SchedulePolicy.occurrences(recurrence, from, to, limit)` lists occurrences in date-then-slot order and is the same over split ranges, so catch-up can resume; `newStart` and `changedStart` hold rules 3 and 7.
A changed schedule is new terms from tomorrow: SELLO-098 ends the old recurrence today and stores the new one.
Next: SELLO-098 (store schedules, catch-up) or SELLO-114 (design pass).
