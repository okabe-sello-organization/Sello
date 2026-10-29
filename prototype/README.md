# Sello prototype

> **Built from the old design.** This prototype walks the v1.1 reference, which is no
> longer the design to build from. The current design is [Sello v2](../docs/design/v2/README.md)
> ([ADR 0013](../docs/decisions/0013-sello-v2-design.md)). The comparison below predates v2.

A throwaway, navigable husk of the [Sello visual reference](../docs/design/sello-spec.html).
It turns the reference's 17 screens into an app you can walk through, to inform future
work. **It is not product code and is never shipped;** the owner uses it to debug
functionality.

- Separate app: `com.software.sello.prototype`, launcher label "Sello Prototipo".
- Depends only on `:design-system`. No domain, data, storage or clock; every figure is
  the reference's fixed October 2026 sample. Nothing is saved.
- Every screen shows a small label at the bottom with its reference number and
  name, such as "4 · Detalle de categoría". Bottom sheets carry their own label.
- Configuración › Prototipo has a screen map that jumps to any screen or state, plus
  switches for first run and empty states.

Build and install: `./gradlew :prototype:installDebug`.

Shortcuts allowed only because it is throwaway: Spanish text lives in the Kotlin
code instead of `strings.xml`, Material icons fill the gaps in `SelloIcon`, and
ktlint's line-length rule is off in this folder.

## Prototype versus where the MVP is heading

Compared on 2026-10-15 with ADR 0001, the open MVP tickets, the post-MVP
roadmap and the app as it was then.
The MVP will be a deliberate subset of this prototype, and several remaining
screens will be simpler than it shows.

### Cut on purpose (approved)
- D01 ships two tabs, Recibo and Resumen. Patrimonio, Metas, investments,
  recurrence, Anotar varios, other currencies, exports and alerts are out of MVP.
  That removes screens 3, 7 and 9–15 and parts of 2 and 16.
- No `$` before amounts (ADR 0007). Deleting a category archives it (D07).
- D01 keeps these on the roadmap, but no tickets exist for them yet.

### Still coming (ticketed)
- Respaldo and "Borrar todos los datos": SELLO-028–032.
- Navigation motion: SELLO-060. Accessibility/adaptive journeys: SELLO-033.
- The time-travel sandbox (SELLO-024–027) is debug-only.

### Drift with no ticket to close it
- **Category detail** (SELLO-018) has no daily amount, comparison with last month,
  weekly or six-month charts or "En números". Its list pages in place instead of a
  separate screen with sort and search.
- **Resumen** (SELLO-022) uses a different structure: net flow and allocation
  slips, a cumulative line and column charts. It has no Cada día gastas, Un mes
  normal, Se fue en, Tu día más caro, records or period sheet.
- **Anotar** shows its receipt inside the form instead of closing with an undo
  notice. On wide windows the forms stay full screen, not 420dp side panels.
- **Smaller:** Configuración keeps the month title and the dock. Recibo's long-press
  menu offers only Editar. First run is a plain empty slip. A past month over its
  limit reads "Sobre el límite", not "Pasado".

Each drift item needs an owner decision: accept it and record it, or ticket it.
