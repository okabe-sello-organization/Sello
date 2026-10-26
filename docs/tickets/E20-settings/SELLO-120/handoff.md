# SELLO-120 — Choose the default currency and order of investments

- What changed: Configuración › Inversiones has Moneda al crear (pesos or dollars) and Orden de la lista (five orders); Nueva inversión starts in the chosen currency and the Inversiones list follows the chosen order. Backups are version 5 and carry both.
- Where: `InvestmentOrderPolicy`, `InvestmentPreferences` and `DataStoreUserPreferences`, the backup wire/codec/validation and `docs/data/backup-format.md`, `SettingsScreen.kt`, `InversionesViewModel`, `InvestmentEditorViewModel`, `RestoreSlips.kt`.
- Still open: the movement screen's investment picker keeps the largest-value order; a FinanceTracker import brings the defaults.
