# SELLO-119 — Lay out Configuración with backup first

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-111, SELLO-100, SELLO-044, SELLO-004
- **Gate:** G2

### Outcome
Configuración opens on Tus datos: a backup card that says when the last backup was made
and lets the person back up or restore from there. Then come Lo que se repite,
Apariencia, Recibo and Inversiones, laid out as screen 16 of the design snapshot with the
owner's changes in the [epic](../README.md).

### Deliverables
- Design system: a settings row (icon, title, optional caption, value, chevron, a loss
  tone, a dashed rule between rows), ink swatches (one round swatch per ink with a ring
  on the chosen one) and an option sheet (a titled list of choices with one selected),
  each with previews and catalog examples.
- Configuración, top to bottom: Tus datos (backup card, then Borrar todos los datos), Lo
  que se repite, Apariencia (Tema segmented, Tinta swatches), Recibo (Orden de las
  categorías and Dirección as rows whose value opens an option sheet), Inversiones (the
  Dólar row, moved from Tus datos) and the footer line.
- The backup card: the last backup's date, "Hoy", "Ayer" or "Hace N días", its size, and
  a stamp: Guardado up to 30 days, Pendiente after 30 days, Sin respaldo when there is
  none. Respaldar (Crear when there is none) shows the backup disclosure in a
  confirmation slip, then the system's save picker, and saves through the same session
  as Respaldo. Restaurar opens the system's file picker; a chosen file is checked and
  previewed on Respaldo, exactly as from there. "Ver qué contiene" opens Respaldo.
- The save's failure, unknown outcome and unreadable record read as on Respaldo, from
  shared code.

### Acceptance criteria
- Tus datos is the first slip; Lo que se repite the second; there is no Avisos slip.
- The stamp follows the 30-day rule by calendar days in the financial zone; a save from
  the card shows Guardando…, then lands the Guardado stamp and the new date.
- Respaldar never opens the picker before the disclosure was shown and accepted;
  cancelling it or the picker changes nothing.
- Restaurar never replaces anything from Configuración: the preview and confirmation
  are Respaldo's. A restore already under way is shown by going to Respaldo.
- Theme, ink, category order and direction still save and persist as before.
- No row is a placeholder: Exportar, Moneda al crear and Orden de la lista are not shown
  until SELLO-121 and SELLO-120 deliver them.
- Loading, unreadable backup record, unreadable counts and rate, and preference failures
  stay visible and honest; large text wraps rows instead of clipping them.

### Tests
- App host: the backup age and stamp rule (0, 1, 30, 31 days, none, zone boundary);
  settings and backup view model changes.
- Design system host/device: the row, swatches and sheet semantics (selected state,
  roles, touch targets).
- App device: Configuración's order of slips, the card's save journey through the
  disclosure, Restaurar handing a file to Respaldo's preview, and the updated
  persistence journey through the option sheets.

### Working checkpoint
On the running app Configuración looks like the approved mockup and every control works.

### Context and starting points
`feature/settings/SettingsScreen.kt`, `feature/backup/` (`BackupSession`, `RestoreSession`
are process-wide), the [design snapshot's screen 16](../../../design/sello-spec.html#s-config)
and the epic's owner direction.

### Implementation plan
1. Design-system components with previews, tests and catalog examples.
2. Backup age rule and the card's state, reusing `BackupViewModel` and `RestoreViewModel`.
3. Rebuild the screen and its navigation hooks; update existing journeys.

### Concrete cases and pitfalls
- The picker's answer can arrive in a new process: keep the launchers in the Root so a
  recreated screen still receives it.
- A backup saved today in Bogotá shortly before midnight UTC is still "Hoy".
- Do not duplicate Respaldo's failure copy or restore preview; share or reuse them.
- Existing journeys open Respaldo with `SETTINGS_BACKUP_TAG`; keep a stable way in.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-119 --retain`.
