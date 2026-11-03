# SELLO-158 — Deshacer after "lograda" lasts its full time after the big moment

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G1

### Outcome
After Marcar como lograda, Deshacer stays for 10 seconds (30 with TalkBack) counted from
when the big moment closes, so it never runs out behind the celebration.

### Acceptance criteria
- Listo tapped 9 s after the save: Deshacer is offered until 19 s after the save.
- With TalkBack, Listo tapped after 40 s: Deshacer stays for 30 s after Listo.
- The offer does not grow on recreation and expires on process death, as today.
- JVM tests with a controlled monotonic clock.

### Notes
Audit findings: `GoalCompleter.kt:92, 156-160` counts from the commit plus a fixed 4 s;
`CelebrationOverlay.kt:222` holds until Listo (its animation runs 7.2 s). SELLO-092
says "Deshacer lasts 10 seconds after the big moment, 30 with TalkBack". Review: lighter.
