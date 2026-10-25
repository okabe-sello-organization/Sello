# SELLO-103 — Tell what Sello recorded on opening

- **Revision:** the gate tested commit `2e4b539` on `sello-103-catch-up-notice`, which merges
  `main` at `50d1ac6` (SELLO-101 done, #134);
  the only uncommitted file was the retained report. The status, this file, the handoff
  and the screenshots are added after.
- **Requirement mapping:** notice and list match SELLO-114's page → the bar above Anotar
  and *Lo que Sello anotó* (`CatchUpNoticeScreenTest`, `artifacts/catch-up-bar.png`,
  `artifacts/catch-up-list.png`); shown while something is unreviewed, never empty →
  `CatchUpNoticeBar` draws nothing for an empty or failed read (screen test), and the
  list follows the entries (`ScheduleRecordReadsTest` on real Room, RE39); every
  catch-up's entries until Listo → `CatchUpNoticeViewModelTest` (RE38, Listo kept on
  the audit clock, a Listo that cannot be kept leaves the notice); each item opens
  `ExpenseEditRoute` or `IncomeEditRoute` → screen test and the journey; no
  notifications or background work → only entries already recorded are read; working
  checkpoint, opening after a week → `CatchUpNoticeJourneyTest` on `semana-fuera@1`
  (RE10: "Sello anotó 4 pagos fijos", the four rows, one opened, Listo).
- **Red / Green:** deliberate breaks, each failing its test: the list read from the
  start instead of the last Listo and closing on a failed Listo (two ViewModel tests);
  the origin filter dropped, listing a manual expense (`ScheduleRecordReadsTest`). The
  journey found the dock showing "Listo, ya revisé lo que Sello anotó": its shown and
  spoken labels were swapped; fixed, and swapping them back fails the journey.
- **Tests:** host `CatchUpNoticeViewModelTest` 6, `DataStoreUserPreferencesTest` +2;
  device `ScheduleRecordReadsTest` 3 (data, real Room), `CatchUpNoticeScreenTest` 6,
  `CatchUpNoticeJourneyTest` 1 (sandbox graph, real Room).
- **Gate results:** `./scripts/verify-ticket SELLO-103 --retain` passed G1 on `2e4b539`:
  1043 host tests, zero failures; lint 0 errors and 34 warnings, as on `main` (a first run
  on `df7027c` found one more, a count before "pagos" that wanted a plural; fixed in
  `2e4b539`). Device tests on `sello098_api30` (API 30): on `df7027c`, after the merge,
  `CatchUpNoticeScreenTest` 6/6, `CatchUpNoticeJourneyTest` 1/1, `ReciboScreenTest` 26/26,
  `ScheduleJourneyTest` 1/1, `ScheduleRecordReadsTest` 3/3 and `ReservedSnapshotTest` 1/1;
  `CatchUpNoticeScreenTest` 6/6 again on the plural change. The hosted check runs G2 on
  the merge result.
- **Quality run:** run 20261025T153842Z-dab2349d; SELLO-103 G1 passed; HEAD 2e4b539, inputs sha256 89389b68cdff, changes sha256 cc355a198441; report docs/tickets/E18-recurrence/SELLO-103/quality-report.json sha256 56bfa8f3ee88aaa590b2e38b1b353d088235dd930993042a3305631ff8002a83
- **Device / Artifact:** `sello098_api30` (API 30); no owner device. Screenshots in
  `artifacts/` from the journey (synthetic sandbox data).
- **Review:** executor self-review; the owner chose bar + list with Listo on
  2026-10-25; owner review by merging.
