# SELLO-068 — Wire the v2 month bar and past-month ribbon into the app frame

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-006, SELLO-004
- **Gate:** G2

### Outcome
The app frame draws its month title with the `V2MonthBar` and has a place for the
`PastMonthRibbon` under it, both built in pull request #54.

### Deliverables
`SelloScaffold` using `V2MonthBar` (month, small year, chevron, settings action) in
place of `MonthSwitcher`'s title, an optional ribbon slot that shows
`PastMonthRibbon` on the band, the catalog scaffold example updated and frame tests.

### Acceptance criteria
- Every tab with the shared month shows the v2 month bar; tapping it opens the same
  month picker, and its spoken label still names the month and year.
- When the caller passes a ribbon, it sits on the band under the month bar; without
  one, nothing changes.
- Large text and narrow windows keep the month button and actions reachable.
- `PastMonthRibbon` and `V2MonthBar` hold no month logic or clock; SELLO-064 decides
  when the ribbon shows.

### Tests
Scaffold layout and semantics tests; catalog example test; G2.

### Working checkpoint
Recibo and Resumen show the v2 month bar on a phone; the catalog scaffold shows the
ribbon slot filled.

### Context and starting points
The components and their tests exist in `designsystem/component/v2/V2Controls.kt`
(`V2MonthBar`, `PastMonthRibbon`) and `V2ComponentTest`. The frame is
`SelloScaffold.kt`; `MonthSwitcher` is in `designsystem/component/Navigation.kt`.

### Implementation plan
1. Draw the title with `V2MonthBar` in `SelloScaffold`, keeping the picker action.
2. Add the ribbon slot and its catalog example.

### Concrete cases and pitfalls
- Pushed screens with a title (Configuración, income history) keep their title bar;
  `V2TitleBar` exists for tab titles.
- `MonthSwitcher` itself is retired in SELLO-073 once nothing uses it.

### Verification recipe
`:design-system`, `:catalog` and shell device tests, then
`./scripts/verify-ticket SELLO-068 --retain`.
