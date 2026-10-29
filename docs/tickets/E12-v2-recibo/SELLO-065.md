# SELLO-065 — Use the full-width Anotar dock and the v2 tab bar

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-015, SELLO-004
- **Gate:** G2

### Outcome
The dock is one full-width **+ Anotar** button and the selected tab is marked by a
pill behind its icon.

### Deliverables
The shell using `V2Dock` and `V2NavigationBar` (built in pull request #54) on every
tab, with the rail keeping its Anotar action. `EntryDock` and `SelloNavigationBar`
are retired in SELLO-073.

### Acceptance criteria
- The dock is a single brand button with a + icon and **Anotar**; it opens expense
  entry as today, including from a past month.
- The selected tab shows a `brandSoft` pill behind its icon; selection is exposed to
  accessibility as today.
- Large text keeps both labels readable; targets stay at least 48dp.
- Wide layouts keep a clear Anotar action in the rail.

### Tests
Catalog examples; shell layout and keyboard-inset tests; G2.

### Working checkpoint
Recibo and Resumen show the v2 dock and tab bar on a phone, and the rail layout
still offers Anotar.

### Context and starting points
Today the dock looks like a field ("Anotar un gasto") with a separate Anotar button,
and the selected tab has a line above it. Code: `EntryDock`, `SelloNavigationBar`,
`SelloNavigationRail` in `designsystem/component/Navigation.kt`; tests
`ShellScreenLayoutTest`, `KeyboardInsetsTest`, `AdaptiveScaffoldTest`.

### Implementation plan
1. Replace `EntryDock` with `V2Dock` and `SelloNavigationBar` with `V2NavigationBar`
   in `SelloScaffold`.
2. Check the rail and every shell route.

### Concrete cases and pitfalls
- The dock is reused on Configuración, Ingresos and category detail; check each.
- The keyboard must not push the dock over content.

### Verification recipe
Design-system and shell device tests, then `./scripts/verify-ticket SELLO-065 --retain`.
