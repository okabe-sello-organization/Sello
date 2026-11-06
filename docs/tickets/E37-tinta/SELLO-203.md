# SELLO-203 — Sello follows the phone's colour

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-202
- **Gate:** G2

### Outcome
On Android 12 and later the person picks Sistema and Sello takes their phone's colour,
in Sello's own shades, and keeps following it when it changes.

### Acceptance criteria
- On Android 12 and later, Tinta shows Sistema between Violeta and Tuya, with a
  wallpaper badge, filled with the ink the phone's accent colour gives (its hue through
  SELLO-201's generator). Tapping it applies at once.
- With Sistema chosen, a caption says "Toma el color de tu fondo de pantalla: <color>.
  Cambia cuando lo cambies.", plus the red-and-green line on those hues.
- When the phone's colour changes, Sello shows the new ink the next time it is on
  screen, and no draft is lost.
- On Android 11 Sistema is not offered. A Sistema choice restored there shows Cobalto,
  and Apariencia shows Cobalto chosen.
- Backups carry Sistema; every older backup version still restores.
- The settings page shows Sistema with its real screenshots.

### Notes
Read the accent from the platform (for example `system_accent1_500`) behind an
injected source, so JVM tests control it and the API level. Storage and backup change:
adversarial review.
