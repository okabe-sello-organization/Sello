# SELLO-215 — Tuya keeps an exact colour

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-214
- **Gate:** G2

### Outcome
Tuya can hold an exact colour that Sello stores, backs up and restores as it is, while a
Tuya hue saved before this epic keeps working and looking the same.

### Acceptance criteria
- Tuya holds either an exact colour (24-bit sRGB, `#000000` to `#FFFFFF`) or a hue saved
  before this epic. A colour is drawn with SELLO-214's palette; a hue exactly as today.
  Nothing stored is converted.
- Saving a Tuya colour stores it and drops Tuya's old hue in the same write, so Tuya never
  has both. Choosing another ink keeps Tuya's colour or hue, as today.
- A stored or restored colour that doesn't parse, or Tuya with both a colour and a hue
  or with neither, is damage and refused, never clipped or repaired, as today's hue rules
  are.
- Backups move to a new version that writes Tuya's colour as `"#RRGGBB"`. Every older
  version still restores, and their Tuya hue keeps working. Older Sello refuses the new
  version.
- Restore and reset texts name a Tuya colour by SELLO-214's name ("Tuya (Gris)"), and a
  hue as today. Reset returns to Cobalto and forgets Tuya's colour and hue.
- Tu tinta still saves a hue until SELLO-216; this ticket changes no screen.

### Notes
Storage and backup change: adversarial review. Cover the AGENTS checklist where it
applies (invalid input, stale or damaged storage, failed read, restart, rollback of a
restore) and name what doesn't (money overflow, Room). Check the backup format against
ARCHITECTURE §7 and the restore's preference completion.
