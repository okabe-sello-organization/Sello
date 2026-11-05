# SELLO-171 — The system bars and the start-up window follow Sello's theme

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
The status-bar and navigation-bar icons stay readable whatever Claro, Oscuro or Sistema
is chosen in Sello. A dark phone no longer flashes a light window at start.

### Acceptance criteria
- The status-bar icons follow what v2 draws under them, in Claro, Oscuro and Sistema:
  white over the band (the tabs and band sub-pages such as Configuración); Sello's theme
  over the desk on entry forms and editors (dark in Claro, light in Oscuro); and over the
  celebration, white in Claro and dark in Oscuro. Changing the choice in Configuración
  updates them at once, without a restart.
- The navigation-bar icons follow Sello's theme on every screen, in 3-button and gesture
  navigation, except the celebration's own window, where both bars follow the flood
  (`--ongain`), as v2 draws it.
- On a dark phone, the window before the first frame is the dark desk colour, not light
  paper. A light phone is unchanged.
- Before and after screenshots of the bars from a new emulator, in the pull request.

### Notes
Audit findings U1 and U11 (2026-11-04): `MainActivity.kt:43, 62-63` calls
`enableEdgeToEdge()` with the default style, which follows the system dark mode.
`res/values/themes.xml` has no night variant. Smallest fix: `SystemBarStyle.auto(…) {
theme.dark }` from an effect keyed on the resolved theme, and a
`values-night/themes.xml` with the desk window background. Review: lighter, with
screenshots.

Criteria corrected on 2026-11-05 by the orchestrator to follow v2: the status bar sits on
the band (`sello.css:40`), not on paper.

Criterion 2 corrected on 2026-11-05 by the orchestrator from review: the celebration's
window follows the flood.
