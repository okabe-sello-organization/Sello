# SELLO-108 — Carry investments through backup, restore and erase

- **Revision:** the gate tested commit `f3dc61f` on `sello-108-investment-backups`, which
  merges `main` at the SELLO-107 merge (#157); the only uncommitted file was the retained
  report. The status, this file and the handoff are added after.
- **Requirement mapping:** older files still restore → versions 1–3 read with no investments
  or rates (`InvestmentBackupTest`, the existing codec fixtures; device
  `InvestmentBackupRestoreTest`: a version 3 file leaves none); new ones round-trip
  exactly, including decimal rates → version 4 writes and reads byte for byte, rates as
  integer hundredths (415025 for 4.150,25, never a decimal), and backup → erase → restore
  gives the same board on real Room (Fondo −258.000, USD +50,50, current rate 4.150,25 on
  20 oct, recording positions kept); restore and reset handle them → `RoomRestores`
  inserts them in the replacing transaction, `RoomResets` clears them (device test), a bad
  file changes nothing (three kinds); format additions with docs →
  `docs/data/backup-format.md` version 4, `docs/development/storage.md`, and the privacy
  page's backup and database rows.
- **Red / Green:** by the delegated agent, each break reverted after: the history check
  disabled (a file with a retiro above the value was accepted), rates written without
  their centavos (the round-trip and byte-for-byte tests failed), restore skipping rates
  (the erase-and-restore device test failed); all passed again.
- **Gate results:** `./scripts/verify-ticket SELLO-108 --retain` passed G1 full on `f3dc61f`:
  1091 host tests in 99 suites, zero failures; lint 0 errors and 34 warnings, as on
  `main`. Device suites on `sello_e18_api30` (API 30), on `0726817` before the merge:
  `:data` 366/366; app `ResetScreenTest`, `RestoreJourneyTest` and
  `StartupFailureScreenTest` 37/37. The hosted check runs them on the merge result.
- **Quality run:** run 20261025T221415Z-2cf38f45; SELLO-108 G1 passed; HEAD f3dc61f, inputs sha256 705e305a83e6, changes sha256 bae89ab9f627; report docs/tickets/E19-investments/SELLO-108/quality-report.json sha256 72bb5c1f73c6c2f28f0b0380226095f1df9cce75c5c9ab378ed22e8b00420c04
- **Device / Artifact:** `sello_e18_api30` (API 30 emulator); no owner device.
- **Review:** built by a delegated agent on SELLO-107's storage; executor review of the
  format, validation and restore; owner review by merging.
