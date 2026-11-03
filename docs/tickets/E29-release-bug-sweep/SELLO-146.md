# SELLO-146 — Catch-up saves whole batches, in the right zone, and announces all of them

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
A catch-up batch is saved whole or not at all, catch-up never writes into restored or
reset data with the old process's zone, and Listo only dismisses entries the person saw.

### Acceptance criteria
- A batch of café (2 Oct) then a payment whose category is archived writes nothing,
  returns a failure, and leaves the checkpoint where it was. Once the cause is fixed,
  the next run records each occurrence exactly once. `Failed` means nothing was written,
  as the port promises; a real-Room test proves it.
- After a restore or a reset commits, catch-up does not run until the app restarts.
  Example: process zone Europe/Madrid at 01:00 on the 15th, restored zone America/Bogota
  (still the 14th): nothing dated the 15th is recorded before the restart.
- Listo and the bar's timeout mark as seen only what the notice showed. An entry
  recorded after the last render stays in the notice. Check this first: if it can't
  happen, say so in the pull request and leave the code as it is.

### Notes
Audit findings: `RoomScheduleCatchUp.kt:108-118` returns `Failed` after inserting rows,
and `CommandProtocol.kt:76-80` commits them; later runs hit the unique origin and stop
for good. `CatchUpRunner.kt:54`, `CatchUpSchedules.kt:33` and `ProcessFinancialZone`
for the zone; `CatchUpNotice.kt:168-174` for Listo. Review: adversarial (storage, recovery).
