# Third-party assets in `:design-system`

Bundled in the package, never fetched at run time. Each licence text ships inside the
APK under `res/raw` so it travels with the asset. Reviewed for SELLO-006 on 2026-10-09.

| Asset | File | Licence | Source | SHA-256 |
| --- | --- | --- | --- | --- |
| Schibsted Grotesk (variable, weights 400–900) | `res/font/schibsted_grotesk.ttf` | SIL Open Font License 1.1, `res/raw/license_schibsted_grotesk.txt` | `google/fonts` at `51303ca`, `ofl/schibstedgrotesk/SchibstedGrotesk[wght].ttf` | `6ceeadf6be8e1fd7687011c7fa38ed0edd1abe967a0b73d97caec183552e823d` |
| Saira Stencil One | `res/font/saira_stencil_one.ttf` | SIL Open Font License 1.1, `res/raw/license_saira_stencil_one.txt` | `google/fonts` at `51303ca`, `ofl/sairastencilone/SairaStencilOne-Regular.ttf` | `781496fdaf8e04cf6741b31025f6b4ba84f66021b097a8e0d85cbea2180cf223` |
| Material Symbols Rounded, weight 500 (individual icons) | `res/drawable/ic_sello_*.xml` | Apache License 2.0, `res/raw/license_material_symbols.txt` | `google/material-design-icons` at `49d4db3`, `symbols/android/<name>/materialsymbolsrounded/` | per file, in Git |

Nine category icons were added for SELLO-016 on 2026-10-10 from the same revision and
path, treated the same way.

Category detail's `sell` and `arrow_upward` icons use the same revision, weight,
filled style and license to match the v1.1 statistics reference.

The debug-only Sello Herramientas launcher in `:app` (`src/debug/res/drawable/ic_tools_launcher_*.xml`)
embeds the `build` (wrench) path from the same revision, weight and filled style, scaled
and cut out of its badge. It ships only in debug builds, which include this module's licence text.

The fonts are unmodified. Icons are the upstream `wght500fill1` (filled) and `wght500`
(outlined) 24px vectors with the AppCompat `android:tint` attribute removed; Compose
supplies the tint. Sello does not use the fonts' reserved names for any derivative.

## Adding an icon

1. Copy the upstream filled vector to `res/drawable/ic_sello_<name>.xml` and, only if
   the reference shows it outlined, the outlined one to `ic_sello_<name>_outlined.xml`.
2. Remove `android:tint` and add the entry to `SelloIcon`.
3. `BundledAssetsTest` inflates every entry.
