# SELLO-119 — Lay out Configuración with backup first

- What changed: Configuración now opens on Tus datos, a backup card with the last backup's date, age, size and a Guardado, Pendiente or Sin respaldo stamp, Respaldar and Restaurar buttons and a link to Respaldo; then Lo que se repite, Apariencia (ink swatches), Recibo (rows opening option slips) and Inversiones (the Dólar rate).
- Where: `feature/backup/TusDatos.kt` (the section, given to Configuración by `SelloApp` because features may not import each other), `feature/backup/BackupAge.kt`, `feature/settings/SettingsScreen.kt`, and `SettingRow`, `InkSwatches`, `OptionSlip` and the content `ConfirmSlip` in `design-system/.../Settings.kt` and `Feedback.kt`.
- Still open: SELLO-120 adds Moneda al crear and Orden de la lista to Inversiones; SELLO-121 adds Exportar to Tus datos, using the same rows and slips.
