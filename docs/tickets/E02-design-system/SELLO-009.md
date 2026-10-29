# SELLO-009 — Build category, adaptive navigation and exact-chart primitives

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-007, SELLO-004
- **Gate:** G2

### Outcome
Supply shared visual building blocks for Recibo and basic Resumen without owning navigation logic.

### Deliverables
CategoryCircle/Cell, scaffold/dock/tab/rail and month-switcher primitives; minimal
bar/line chart wrappers with exact render fixtures, selection values and accessible
text equivalents. Add all type groups/states to catalog with stable IDs.

### Acceptance criteria
- Category meaning uses names/icons and brand ink, not per-category colors.
- Width/height adaptation follows Sello: compact, short landscape, medium and expanded;
  dock/insets never cover content. Navigation callbacks are stateless.
- Chart geometry may approximate, but selected amounts and textual equivalents
  use original exact values. Charts are ≥130dp with at most one labeled reference.
- Customer app remains free of catalog chrome and synthetic data dependencies.

### Tests
Chart selection at large amounts, accessible text/value agreement, layout/inset
and navigation-callback tests, four-scheme catalog walkthrough; run G2.

### Working checkpoint
App can compose the primitives without importing catalog; independent catalog groups
show every delivered component and do not advertise unfinished product screens.

### Context and starting points
Recibo and Resumen need shared category/navigation/chart rendering, not separate
copies. Read [categories](../../design/sello-spec.html#c-categorycircle),
[scaffold](../../design/sello-spec.html#c-selloscaffold), navigation/a11y sections
and exact render-input contracts from SELLO-007. No financial aggregate policy is owned here.

### Implementation plan
1. Implement CategoryCircle/Cell using explicit presentation progress/status/name/icon;
   clamp drawing progress only, retaining exact over-limit sign/text.
2. Add scaffold/dock/tabs/rail/month controls with callbacks and inset slots. Support
   two MVP tabs without hardcoding disabled Patrimonio in a reusable component.
3. Implement bar/line chart render inputs retaining IDs and exact labels/values;
   normalize geometry separately. Selected point maps back to original data, not pixels.
4. Implement compact/short-height/medium/expanded layouts and accessible chart summary
   or equivalent values. Navigation state/business month validation belong to app.
5. Register examples with near-maximum amounts, no limit, negative remaining, empty
   chart and different window sizes. Keep complex future investment charts out of MVP.

### Concrete cases and pitfalls
- Two adjacent amounts above floating-point exact range may draw similarly but
  must select/announce their distinct exact values.
- Under 480dp height use the reference's rail/two independently scrollable panes;
  medium content cap and expanded pane/sheet widths come from the local reference.
- A custom Canvas without text semantics/keyboard selection is not an accessible chart.

### Verification recipe
Create/run `*ChartExactSelectionTest`, `*CategorySemanticsTest` and adaptive scaffold
UI tests; run G2 for design-system/catalog. Inspect insets, tap targets and text
summaries against fixture IDs/values rather than judging screenshots alone.

### Execution progress
2026-10-09: started on branch `sello-009-category-navigation-charts` after SELLO-008
merged. No product decision or dependency was needed: architecture §3 already says
charts start as small Compose drawings, not a chart library.

Delivered in `:design-system`: `CategoryCircle`, `CategoryCell`, `CategoryGrid`;
`selloWindowLayout` and `SelloScaffold` with `Blotter`; `SelloNavigationBar`,
`SelloNavigationRail`, `EntryDock`, `MonthSwitcher`, `MonthGrid`; `ColumnsChart`,
`LineChart` and `SlipHeading`; four more icons. The contracts are in
`docs/development/layout-and-charts.md`. New catalog example IDs:
`categories.category-circle`, `categories.category-cell`, `navigation.scaffold`,
`navigation.entry-dock`, `navigation.navigation-bar`, `navigation.month-switcher`,
`charts.columns`, `charts.line`. Every one of the nine component types now has examples.
The catalog gained a "Landscape 760×400" window option and full-screen examples.

Choices made during the work, for owner review:
- The window breakpoints are the reference's, implemented as a small pure function
  with no Material adaptive dependency. Architecture §6 says such APIs "should" be used;
  they can replace this function later without changing the scaffold's contract.
- The dock and tab bar are laid out under the content instead of floating over it, so
  content cannot be covered. The paddings handed to the content include the system bars.
- Navigation takes a list of destinations, so two tabs work today and a third needs no
  disabled placeholder. The rail's "+" and the dock are the same action.
- The dock label has no `$`, following ADR 0007.
- Charts take a short `valueLabel` and reference label from the app; exact amounts are
  never read back from the drawing.
- `MonthGrid` shows one year of months and reports picks. The bottom sheet that holds
  it, the "Volver a octubre" chip and sideways month swiping belong to the screens.
- The reference's Kotlin sketches take raw amounts and `ImageVector`; these take
  formatted values and `SelloIcon`, as SELLO-006 and SELLO-007 established.
- All components stay in the `component` package used since SELLO-007, not the
  per-type packages the execution guide suggests as targets.

Left for later: hiding the dock and tab bar under the system keyboard is implemented
but has no automated test. The medium window's side-panel sheet, shared-element dock
transition, tab cross-fade and blotter behaviour on real lists belong to navigation and
screen work in E04. `SelloProgressBar` and the investment charts are post-MVP or later
tickets. The customer app does not compose these primitives yet.

2026-10-09: owner approved pull request 8, including the choices above; moved to Done
for a squash merge. All four tickets of this epic are Done, so the epic is Done.
