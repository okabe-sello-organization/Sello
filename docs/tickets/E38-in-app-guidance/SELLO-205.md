# SELLO-205 — Sello remembers which guidance was seen

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
Sello remembers which nudges a person has seen or closed and their backup-reminder
choice, keeps that through Restablecer, and carries it in backups, so nobody is told the
same thing twice.

### Acceptance criteria
- A domain port records, per nudge (Guía hint, backup reminder, Anotar varios, pagos
  fijos, arrastre, borrar, inversiones, meta en Recibo): seen/closed. It also records
  the day the last nudge was shown (for the one-a-day rule), the day the backup reminder
  was last shown, and "No volver a mostrar" for the backup reminder. Keys are stable
  strings, never ordinals or class names.
- **Restablecer keeps all of this.** Today `resetAll()` clears every preference: reset
  must clear the rest exactly as before and leave these alone.
- **Backups carry it** in a new backup version (11). Every older version still restores.
- **Restoring merges:** a nudge counts as seen if the phone or the backup says so;
  "No volver a mostrar" holds if either says so; the later of the two dates wins. A
  backup without these fields keeps what the phone has. A stored value that can't be
  read is rejected as today's preferences are, never turned into "seen" or "not seen"
  silently.
- **People already using Sello:** the first time the new version starts, if the phone
  has any expense, income, goal or investment, every new-user nudge is marked seen;
  the Guía hint and the backup reminder are not. This runs once and never again.
- A failed read keeps showing nothing new (no nudge appears from a failed read); a failed
  write leaves the nudge to show again later and never crashes or blocks the screen.
- Tests: real DataStore on the JVM for the keys; the backup codec round-trip with and
  without the new fields; restore merge cases; reset keeps the keys and clears the rest;
  the one-time seeding with and without existing data, and after process death midway.

### Notes
- Adversarial review: preferences, backup format, restore and reset.
- Existing patterns: `PAST_EXPENSE_SEEN`, `CATCH_UP_NOTICE_SEEN` and `PortablePreferences`
  in `data/.../preferences/DataStoreUserPreferences.kt` and `data/.../backup/`.
- Say in the pull request which items of the AGENTS money/storage checklist don't apply.
