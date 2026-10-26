# SELLO-120 — Choose the default currency and order of investments

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-119, SELLO-004
- **Gate:** G2

### Outcome
In Configuración › Inversiones a person picks the currency a new investment starts in
(COP or USD) and how the Inversiones list is ordered.

### Deliverables
- Two portable preferences: Moneda al crear (COP default, USD) and Orden de la lista
  (Mayor valor default, Rendimiento, Alfabético, Más reciente, Última actualización),
  stored in DataStore, carried by backups and cleared by reset.
- The rows in Configuración › Inversiones, using SELLO-119's row and option sheet.
- Agregar inversión starts in the chosen currency (still asking for the rate first when
  USD has none); the Inversiones list follows the chosen order.

### Acceptance criteria
- Each order is defined with deterministic ties, and investments without a value stay
  last as today.
- A backup made with the new preferences restores them; an older backup restores the
  defaults; the backup format version and its validator change together.
- Reset returns both to their defaults.

### Tests
- Domain/app host: each order with ties and missing values, from independent examples.
- Data host/device: preference read/write failures, backup round trip and older files.
- App device: changing both rows and seeing Agregar inversión and the list follow.

### Working checkpoint
Changing either row changes Agregar inversión or the list, and survives a backup restore.

### Context and starting points
[ADR 0017](../../../decisions/0017-goals-recurrence-investments.md); `InversionesViewModel`
orders largest value first today; `BackupWire`/`BackupValidation` hold the portable preferences.

### Implementation plan
1. Preferences and their port.
2. Backup format, validation and reset.
3. Ordering and the editor default; the rows.

### Concrete cases and pitfalls
- "Rendimiento" with no contributions has no percentage: say where those go.
- An older Sello must reject the newer backup format, not misread it.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-120 --retain`.
