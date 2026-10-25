# SELLO-044 — Import legacy data through the validated restore path

- What changed: Restaurar now also accepts a FinanceTracker backup. It is translated into a Sello backup, previewed with what was changed and left out, and restored by the normal restore (ADR 0019).
- Where: `data/.../backup/legacy/LegacyTranslator.kt`, `RoomRestores.stage`, `presentation/restore/LegacyReport.kt`, [legacy export format](../../../data/legacy-export-format.md).
- Still open: investments, their movements and rates are reported as not imported yet; SELLO-118 imports them after E19.
