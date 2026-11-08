# SELLO-211 — Recibo points to the Guía and reminds to back up

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-204, SELLO-205, SELLO-206
- **Gate:** G2

### Outcome
After their first expense the person learns where the Guía is, and about once a month,
without a backup, a quiet line reminds them their data lives only on this phone.

### Acceptance criteria
- **Nudge rules** (a domain rule every nudge uses): one nudge at a time on a screen and
  at most one a day across the app; a nudge closes for good when its action is done or
  its ✕ is tapped, or once the day it showed has passed. The rule is tested with a
  controlled clock across midnight and a time-zone change.
- **Guía hint** on Recibo, below the summary card, after the first expense is saved:
  "¿Dudas? La Guía de Sello está en Configuración." with a ✕, as in the
  [mock](attachments/guidance-mock.html). Tapping the text opens the Guía; either closes
  it for good. People already using Sello see it once after the update.
- **Backup reminder** on Recibo, in the same place, using the nudge row with a pill
  (option B): "Tu último respaldo fue hace N días. Tus datos viven solo en este
  teléfono." (or, with no backup ever, a line saying there is none yet), the pill
  **Ir a Respaldo** on the right and **No volver a mostrar** as quiet text on the left.
  - Shows only when there is at least one recorded movement and 30 days have passed
    since the last backup, or since the first movement when there is no backup.
  - Stays for that day, then waits 30 days. Making a backup clears it at once.
  - Ir a Respaldo opens Configuración's Respaldo; it does not start a backup.
  - No volver a mostrar hides it for good; Configuración's Pendiente stamp still shows.
  - The days count uses the stored last-backup time and the injected clock; a failed
    read of that time shows no reminder rather than a wrong number.
- Neither shows while Recibo is loading, failed or in its empty state, and neither
  covers or pushes the summary card's figures out of view.
- ViewModel tests cover each rule above with a controlled clock; screenshot tests show
  both rows in light and dark.

### Notes
- "Or once the day it showed has passed": aligned with the owner's "shows once" direction
  (2026-11-08) on the orchestrator's ruling.
- Lighter review unless the work changes storage beyond using SELLO-205's port; then
  adversarial.
- The backup time is `BACKUP_AT` in `DataStoreUserPreferences`.
