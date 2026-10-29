# SELLO-073 — Retire the v1 components v2 replaced

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-065, SELLO-068, SELLO-070, SELLO-071, SELLO-072, SELLO-004
- **Gate:** G2

### Outcome
The app and catalog use one set of components: each v1 component that a v2 one
replaced is deleted, and the remaining v2 components lose their `V2` prefix.

### Deliverables
Removal of superseded v1 components and their catalog examples and tests (for
example `EntryDock`, `SelloNavigationBar`, `MonthSwitcher`'s title, `FieldPill` where
`EntryLine` replaced it, `ChipFlow` where `V2ChipRows` replaced it), the `V2` names
renamed to their final names, the catalog's V2 section folded into its component
groups, and the remaining screens (category detail, the category editor,
Configuración) moved onto the shared components where an equivalent exists.

### Acceptance criteria
- No production screen imports a deleted component; the architecture check passes.
- Each deletion is backed by a search showing no remaining use.
- Category detail, the editor and Configuración look as before (ADR 0013 v1.1) apart
  from shared pieces now drawn by the v2 component.
- The catalog lists each component once.

### Tests
Full `:design-system`, `:catalog` and `:app` suites; G2.

### Working checkpoint
The catalog lists each component once, and the app runs every journey with no v1
duplicate left.

### Context and starting points
List v1 components with `grep` over `designsystem/component` and compare against
`designsystem/component/v2`. The catalog's `v2-screens` section is review material;
keep or remove it with the owner.

### Implementation plan
1. List each v1/v2 pair and its remaining uses.
2. Move remaining uses, delete the v1 component and its example and tests.
3. Rename v2 components and fold catalog sections.

### Concrete cases and pitfalls
- Some v1 components have no v2 replacement (for example `CategoryCell`); keep them.
- Renames touch many files; do them in one focused commit without behaviour changes.

### Verification recipe
Full suites, then `./scripts/verify-ticket SELLO-073 --retain`.
