# SELLO-145 — Changing a schedule keeps it readable and never records the past

- **Type:** Task
- **Priority:** P0
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
Changing a fixed payment's repeat, end or start never makes the schedule unreadable,
never records occurrences dated today or earlier, and keeps a pause the person set.

### Acceptance criteria
- Twice a month on 15 and 30, last recorded 30 Oct (second slot). On 2 Nov it becomes
  monthly on day 15 from 3 Nov: the change commits, the schedule reads back, and Recibo,
  Pagos fijos, catch-up and the backup export keep working. 30 Oct stays; next is 15 Nov.
  The same holds for twice a month → every N days and → every N weeks.
- Rent monthly on 5, 950.000, ended 30 Sep, last recorded 5 Sep. On 20 Oct its end is
  removed, or moved to 31 Dec: nothing is recorded for 5 Oct; the next entry is 5 Nov.
  An ended daily schedule given a later end records nothing dated before tomorrow.
- A schedule starting 1 Dec, paused on 10 Oct (the pause starts 1 Dec), is listed under
  En pausa. Moving its start to 20 Nov moves the open pause's start with it, so 25 Nov
  is not recorded.
- Red-first real-Room tests for each case above; no stored row is repaired on read.

### Notes
Audit findings: `RoomScheduleCommands.kt:225-246` leaves `handled_slot = 2` that
`ScheduleMapper.kt:117` refuses for a rule that isn't twice-monthly;
`RoomScheduleCommands.kt:189-200` ignores the end in `ruleChanged` and keeps the old
checkpoint; `ScheduleListPolicy.kt:63` and `RoomScheduleCommands.kt:208-214` for the
pause. Rules 7 and 8 of [recurrence examples](../../testing/recurrence-examples.md).
Review: adversarial (storage).
