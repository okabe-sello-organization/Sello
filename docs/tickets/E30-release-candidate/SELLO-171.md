# SELLO-171 — The system bars and the start-up window follow Sello's theme

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
The status-bar and navigation-bar icons stay readable whatever Claro, Oscuro or Sistema
is chosen in Sello. A dark phone no longer flashes a light window at start.

### Acceptance criteria
- Phone light with Sello Oscuro: light bar icons. Phone dark with Sello Claro: dark bar
  icons. Sistema follows the phone. Changing the choice in Configuración updates the
  bars at once, without a restart.
- Both 3-button and gesture navigation are readable in each combination.
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
