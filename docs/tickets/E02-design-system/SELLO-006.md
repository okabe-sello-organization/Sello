# SELLO-006 — Implement Sello tokens and the independent catalog shell

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-003, SELLO-004
- **Gate:** G2

### Outcome
Make typography, colors, spacing, shapes and motion authoritative reusable assets.

### Deliverables
SelloTheme with Cobalto/Violeta × light/dark, Material mappings, semantic gain/loss
tokens, bundled licensed fonts and Rounded symbols. Catalog home grouped by component
type with local theme/font/window controls; stable example IDs and token previews.

### Acceptance criteria
- No dynamic Material palette silently replaces Sello's inks; semantic meanings
  remain unchanged across inks. Stencil type is limited to stamps.
- Catalog uses its own launcher/application ID, installs beside app and accesses no
  financial repository, production DI, storage/network permission or customer data.
- Catalog controls and fixtures are not part of the shared component runtime API.

### Tests
Theme/token mapping and contrast checks, four-scheme previews, catalog launch/theme
interaction UI tests and dependency inspection. Run G2 for catalog/theme boundaries.

### Working checkpoint
App renders through the same theme; catalog is a real independent executable,
not documentation screenshots or duplicated composables.

### Context and starting points
The template theme is not Sello and per-screen styling would fork immediately.
Use modules/IDs from SELLO-003, accepted D01/D02 and
[foundations](../../design/sello-spec.html#foundations). Read architecture §6,
the guide's module map and Compose testing/accessibility sources.

### Implementation plan
1. Inventory the reference's four color schemes/type/spacing/shape/motion tokens;
   implement typed providers in design-system/theme with Material mappings.
2. Bundle reviewed fonts/icon assets and license notices; remove template palette
   ownership after app points to the new theme. No runtime font/network fetch.
3. Add catalog home/routes grouped by component type, stable example IDs and local
   theme/font/window selection. Initially show implemented foundations only.
4. Add token/type samples, long monetary text samples and contrast checks in all
   schemes. Keep catalog controls/fixtures in catalog, not the reusable theme API.
5. Verify independent launch/package permissions and apply the theme in the existing
   customer screen; preference persistence comes later in SELLO-023.

### Concrete cases and pitfalls
- Switching Cobalto to Violeta changes brand ink, not gain/loss meaning.
- Saira Stencil One is for stamps; minimum readable text is 12sp. System theme
  default must not override selected ink or introduce dynamic Material colors.
- Browser Google Fonts loading is not proof app assets are bundled/licensed;
  test actual APK resources and offline launch.

### Verification recipe
Create `*SelloThemeTest` and catalog launcher/control UI tests. Run catalog host
tests/assembleDebug plus catalog connected tests and G2; record four-scheme previews,
contrast results, bundled assets/licenses and distinct application IDs.

### Execution progress
2026-10-09: started on branch `sello-006-tokens-catalog-shell` after SELLO-005 merged.
The owner chose to ship Material Symbols Rounded as individual vectors, not the
15.2 MB icon font. The two text fonts were already fixed by the architecture.

Delivered in `:design-system`: `SelloColors` for Cobalto/Violeta × light/dark with a
shared `semantic` group, the full Material mapping, `SelloTypography` plus stamp-only
`SelloType`, `SelloShapes`, `SelloSpacing`, `SelloMotion`, `SelloIcon` with a starter
set of ten icons, the two bundled fonts and three licence texts
(`design-system/THIRD_PARTY_NOTICES.md`). `SelloTheme(ink, darkTheme)` no longer has a
dynamic-colour path. Delivered in `:catalog`: a home grouped by component type, six
foundations examples with stable IDs, and local ink, mode, font-scale and window-width
controls. The customer app's placeholder screen renders through the same theme.

Choices made during the work, for owner review. Dark `errorContainer` uses the
documented `lossSoft` value `#5A171B`; the reference's stylesheet also carries an
unused `#6B1A1F`. Material slots the reference does not name (fixed, inverse, tint,
low/highest containers) are fed from the nearest Sello token so none keeps a Material
baseline colour. Roles the type table omits (`headlineLarge`, `headlineMedium`,
`titleSmall`, `bodySmall`) are interpolated in Schibsted Grotesk. Selecting a weight
from the variable font needs Compose's experimental `Font(variationSettings)` API in
BOM 2026.02.01, opted into in one place. The only dependency change is Compose UI test
for the catalog's device tests; no existing locked version moved.

Left for later tickets: reduced-motion handling and the tightened thousands point
belong to the components that animate and print money (SELLO-007); the status bar does
not follow the catalog's own light/dark choice until the scaffold exists (SELLO-009);
saving the chosen ink is SELLO-023. Catalog width presets cover compact, medium and
expanded; short-landscape is checked by rotating the device.

2026-10-09: owner approved pull request 4; moved to Done for a squash merge. The catalog
and debug app were also installed on the owner's phone (Android 16, API 36) over USB.
