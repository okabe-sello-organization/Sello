# SELLO-121 — Export everything to CSV or JSON

- What changed: Tus datos has Exportar a CSV o JSON. A CSV (one table of every money movement, spreadsheet-safe) or a JSON (also categories with limits, goals, investments, rates and recurring items) is saved through the picker or shared through the share sheet; neither is a backup.
- Where: `data/.../export` (`ExportRows`, `CsvExport`, `JsonExport`, `RoomDataExports`), `feature/backup/ExportSession.kt`, `ExportViewModel.kt`, `ExportControls.kt`, `platform/AndroidExportShares.kt`, the FileProvider in the manifest with `res/xml/export_paths.xml`, `gradle/privacy.gradle.kts` and `docs/privacy/mvp-data-handling.md`.
- Still open: nothing for E20. A shared copy is the receiving app's; Sello removes only its own cached file.
