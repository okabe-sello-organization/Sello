# SELLO-100 — Manage recurring payments and incomes

- **Revision:** the gate tested commit `8fe5f95` on `sello-100-schedules-entry`, which
  merges `main` at `c866592` (SELLO-114 done, #131); the only uncommitted file was the
  retained report. The status, this file, the handoff and the screenshots are added after.
- **Requirement mapping:** the screens match SELLO-114's locked page → Se repite in both
  entry forms, Pagos e ingresos fijos from Configuración and Resumen, the editor in the
  entry layout and Solo este / Este y los siguientes (screen tests, the journey, the
  screenshots in `artifacts/`); each kind is set with the Sello pad → `AmountKeypad` in
  both entries and the editor; changes say they apply from now on → "Los cambios aplican
  desde mañana…" and `ChangeSchedule` from tomorrow (`ScheduleEditorViewModelTest` RE17,
  RE18); deleting keeps recorded occurrences → `ScheduleJourneyTest` on real Room;
  working checkpoint, schedules managed on a phone → the journey on an API-30 emulator.
- **Red / Green:** `RepeatChoiceTest` (7) and `ScheduleListPolicyTest` (4) failed on stubs,
  then passed. Deliberate breaks, each failing a test: the schedule never sent (expense
  and income), the date not kept today-or-later, recovery asking the expense commands, an
  incomplete choice kept, a failed read shown as loading, the tab not restored, a rule
  change keeping the old start, deleting without asking, a restored draft sent over a
  newer version, no recovery after a restart, Solo este never asked, the entry edited
  before the schedule, the schedule change resent on retry, a tappable or unspoken part.
  Two breaks first stayed green and their tests were strengthened.
- **Tests:** host `RepeatChoiceTest` 8, `ScheduleListPolicyTest` 4,
  `SchedulesViewModelTest` 4, `ScheduleEditorViewModelTest` 9, entry ViewModels +16
  (Se repite, Solo este); device `ScheduleJourneyTest` (real Room: create from today,
  catch-up records it with its origin, pause, delete keeping the income),
  `ScheduleScreensTest` 7, `ExpenseEntryScreenTest` +4, `RecurrencePartsTest` 16 and the
  catalog examples. Fixed during verification: notices on the list, a paused row's
  caption squeezed at 320dp, a day-grid test that assumed a 360dp screen.
- **Gate results:** `./scripts/verify-ticket SELLO-100 --retain` passed G1 on `8fe5f95`:
  1010 host tests, zero failures; lint 0 errors and 34 warnings, as on `main`. Device
  suites on `sello098_api30` (API 30): app 427/427 and catalog 22/22 on `689f22b`;
  design-system 178/179 there, the one failure fixed in `364135f` and
  `RecurrencePartsTest` 16/16 on `71aac2b` (later commits touch only that row, two
  strings and this report's tracking). The hosted check runs G2 on the merge result.
- **Quality run:** run 20261025T071900Z-e320a6ee; SELLO-100 G1 passed; HEAD 8fe5f95, inputs sha256 7a5c61c58af5, changes sha256 566a978fc95b; report docs/tickets/E18-recurrence/SELLO-100/quality-report.json sha256 22121a02f093ccd6e7b70a12c1272e6da8c2bd9d01a3b3c76b0158b02a5a97cc
- **Device / Artifact:** `sello098_api30` (API 30); no owner device. Catalog screenshots
  in `artifacts/` (synthetic).
- **Review:** executor self-review; owner answered the design questions on 2026-10-25;
  design-system parts built by a delegated agent and reviewed here; owner review by merging.
