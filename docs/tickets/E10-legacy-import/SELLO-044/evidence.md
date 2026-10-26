# SELLO-044 — Import legacy data through the validated restore path

- **Revision:** the gate tested commit `a721821` on `sello-044-legacy-import`, on `main`
  at `c8d0b4c`; no other file was dirty. The retained report, this file, the handoff and
  the Done status are added after that commit.
- **Requirement mapping:** restore as much as possible, skip and count what breaks a rule,
  only the owner's two repairs → `LegacyTranslator` (`data/.../backup/legacy`), proved by
  `LegacyTranslatorTest` (23 cases: every skip reason, dependants, duplicates, clashes,
  cuts by code point, bounds, unusable and newer files); nothing skipped silently, preview
  by kind and reason with cancel → `LegacyReportSection` in the restore preview, report
  kept in the staging manifest, proved by `LegacyImportRestoreTest` (report survives a
  restart, cancel changes nothing) and `RestoreJourneyTest.aFinanceTrackerFileShowsWhatChangedAndIsRestored`;
  dependants skipped, never moved → `CategoryLeftOut` cases in both tests; translator never
  writes, the released validator accepts the translation unchanged and restore applies it
  with its warning, generation advance and receipt → `the released validator accepts the
  translation unchanged` and `RoomRestores.stageLegacy` encoding through `BackupCodec`,
  proved on real Room (generation 2, rows counted by hand); document picker only, not a
  FinanceTracker file or beyond bounds rejected whole → recognition and bounds cases in
  both tests; no personal data → synthetic fixtures only; the owner's real export was read
  locally for its shape and once by a deleted probe that printed counts only.
- **Red / Green:** `./gradlew :data:testDebugUnitTest --tests '*LegacyTranslatorTest'`
  against a stub that recognised nothing: 23 tests, 22 failed on behaviour; implemented,
  23 passed. Probing the owner's export then rejected it whole: FinanceTracker writes
  `exportedAt` as `yyyy-MM-dd HH:mm`, not ISO. The fixtures were changed to that form
  first (20 of 23 failed), then the parser (23 passed); the export then translated with
  only investments, movements and rates reported as not imported yet.
- **Gate results:** `./scripts/verify-ticket SELLO-044 --retain`: G1 full passed
  (tickets-check, quality-tests, performance-tests, architecture, links, gradle-host);
  34 lint warnings, none in files this ticket touches; no failures.
- **Quality run:** run 20261026T021237Z-895fc50f; SELLO-044 G1 passed; HEAD 7f050e2, inputs sha256 bad84d7473a2, changes sha256 bd14b2215b59; report docs/tickets/E10-legacy-import/SELLO-044/quality-report.json sha256 356b590ddfd0071d33f7dac0316344d5a907db8e18cebacbf7b6fd703473826e
  (after merging main with E19 once; the translator now passes empty investments and rates)
- **Device / Artifact:** focused device runs on a new API 30 emulator (`sello044-legacy`,
  `emulator-5730`): `:data:connectedDebugAndroidTest` for `LegacyImportRestoreTest` and
  `RestoreCommandContractTest`, 18 passed; `:app:connectedDebugAndroidTest` for
  `RestoreJourneyTest`, 5 passed. Preview capture: [legacy-preview.png](artifacts/legacy-preview.png)
  (synthetic data). The full device suites run in the hosted `quality` check on the merge result.
- **Review:** self-review of the translator, staging and preview against ADR 0019 and the
  legacy export format; the merge of this ticket's pull request is the owner's approval.
