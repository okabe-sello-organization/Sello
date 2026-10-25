# SELLO-099 — Carry schedules through backup, restore and erase

- **Revision:** the gate tested commit `0ab4170` on `sello-099-schedule-backups`, which
  merges `main` at `41a08ee` (SELLO-098 done, #123); the only uncommitted file was the
  retained report. The status and this file are added after that commit.
- **Requirement mapping:** restored schedules do not re-record what the backup holds →
  checkpoints and origins travel in version 3 and restore keeps them
  (`ScheduleBackupRestoreTest`: catch-up records nothing on the day of the backup, then
  only 30 October and 5 November); an origin past its schedule's checkpoint is refused
  when the file is read (`ScheduleBackupTest`); versions 1 and 2 still restore, with no
  schedules or origins (`ScheduleBackupTest`, the existing G01 and goal files); restore
  replaces rather than merges, erase counts and removes (`ScheduleBackupRestoreTest`);
  working checkpoint, a backup with schedules restoring and catching up correctly →
  the first round-trip test. Scope added with the owner: resuming into an archived
  category is refused (`ScheduleCommandContractTest`).
- **Red / Green:** the resume test failed on the device before the fix
  (`Committed` instead of `Rejected(CategoryArchived)`), then passed. Nine deliberate
  breaks each fail a test: an origin past its checkpoint accepted, a running payment into
  an archived category accepted, slot 2 accepted on a monthly checkpoint, schedules or
  origins not written, restore leaving schedules out or dropping checkpoints, export
  reading no schedules, erase counting none.
- **Tests:** host `ScheduleBackupTest` (9) with `valid-schedules-v3.json`; G01 and goal
  files written as version 3 (`valid-g01-v3.json`, `valid-goals-v3.json`); newer-version
  checks moved to version 4. Device `ScheduleBackupRestoreTest` (3), the resume refusal,
  `ResetScreenTest` (+2). Existing tests building summaries or snapshots by hand pass a
  schedule count; the erase and restore contracts count the one schedule their history
  holds.
- **Gate results:** `./scripts/verify-ticket SELLO-099 --retain` passed the local
  requirement, G1, on `0ab4170`: 947 host tests, zero failures, errors or skips; lint
  zero errors and 34 warnings, as on `main`. Device suites on the dedicated AVD
  `sello098_api30` (API 30): data 309 on `0ab4170`, app 406 on `edd15ec` (the commit
  after changed only data device tests), zero failures. The hosted check runs G2 on the
  merge result.
- **Quality run:** run 20261025T012926Z-46027c47; SELLO-099 G1 passed; HEAD 0ab4170, inputs sha256 993cd4d9c39f, changes sha256 8cb963a8e79b; report docs/tickets/E18-recurrence/SELLO-099/quality-report.json sha256 c5ae34eea7c07e588bd2aef52be0ef511bb8769e917905debf697ccbbd0d6290
- **Device / Artifact:** `sello098_api30` (API 30), created for this epic; no owner device used.
- **Review:** executor self-review; owner approved the wording and the added resume fix;
  owner review by merging.
