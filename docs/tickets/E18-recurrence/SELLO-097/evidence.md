# SELLO-097 — Calculate schedule occurrences

- **Revision:** the gate tested commit `a81c6be` on `sello-097-schedule-occurrences`, on
  `main` at `52a377b`; the only uncommitted file was the retained report. The status and
  this file are added after that commit.
- **Requirement mapping:** matches every approved example: month ends and anchors (RE01,
  RE02), start and end (RE03, RE04, RE09), twice a month (RE05, RE06 with two slots on
  one date), every N days or weeks (RE07, RE08), changed rules from tomorrow (RE18–RE20),
  pauses and archiving (RE21–RE23) → `SchedulePolicy.occurrences`, `newStart`,
  `changedStart` and the checked `RepeatRule` and `Recurrence`, each in
  `SchedulePolicyTest`. Every 15 days and twice a month differ (RE07); pauses skip
  occurrences and an end date stops them (RE09, RE21–RE23).
- **Red / Green:** `./gradlew :domain:test --tests '*SchedulePolicyTest'` against a
  skeleton returning no occurrences and accepting any start: 18 of 21 failing on
  behaviour (expected dates, `TooEarly`). Green: 21 of 21. Ten deliberate breaks of the
  policy (no clamp, merged slots, resume day paused, every N rounded down, end excluded,
  start ignored, open pause ignored, both start checks loosened, limit ignored) each fail
  at least one test.
- **Tests:** host `SchedulePolicyTest` (21): the examples above, rule and recurrence
  validation, a daily schedule from year 1 reaching 9999 directly, and 1.500 seeded random
  schedules checked for order, uniqueness, a day-by-day scan, split ranges and limits.
- **Gate results:** `./scripts/verify-ticket SELLO-097 --retain` passed all seven G2 checks.
  Host: 855 tests, zero failures, errors or skips; lint zero errors and 34 warnings, as on
  `main`. Device: data 269, design-system 143, app 373, catalog 21, zero failures.
- **Quality run:** run 20261024T215119Z-c07a8e17; SELLO-097 G2 passed; HEAD a81c6be, inputs sha256 b0bd4f873ace, changes sha256 62d6e332c0e4; report docs/tickets/E18-recurrence/SELLO-097/quality-report.json sha256 2cb0765fcc722564518e4b8cc365118a603fa7f0aabb6eda44220c0493be0b99
- **Device / Artifact:** the gate ran on the dedicated AVD `sello097b_api30` (API 30),
  shut down afterwards. Extra, on the owner's phone (SM-S731B, API 36), without the three
  tests that change device-wide state: data 269 passed; design-system and catalog one
  layout failure each (`EntryControlsTest`, `EntryCatalogInteractionTest`), which pass on
  API 30 and touch no code changed here; the app suite stopped when the phone's adb shell
  stopped answering, so it is not evidence.
- **Review:** executor self-review; owner review by merging.
