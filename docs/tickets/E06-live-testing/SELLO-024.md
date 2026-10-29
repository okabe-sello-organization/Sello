# SELLO-024 — Create isolated sandbox sessions and financial time controls

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-005, SELLO-011, SELLO-015, SELLO-004
- **Gate:** G2

### Outcome
Live testing can freeze/advance dates without touching production data or unrelated clocks.

### Deliverables
Debug-source-set tools entry, explicit sandbox badge, dedicated sandbox Room/
preferences namespace and session lifecycle; freeze/day/month/date controls and
bounded play/pause using the observable financial clock. Persist time/zone/profile
for process-restart testing; keep real audit and monotonic sources independent.

### Acceptance criteria
- Debug application ID differs from production; sandbox switching also preserves
  the normal debug profile. Switching/reset cannot race an active financial write.
- Device time, undo expiry, animation and network timeout never accelerate. Date
  changes trigger normal snapshot/rollover paths, not debug-only calculations.
- Rewind after mutation requires confirmed scenario reset/replay; a displayed old
  date cannot pretend newer persisted history never happened.
- Tools/classes/resources/routes have no release dependency; no “hidden menu” substitute.

### Tests
Clock separation, persistence/restart and switch/session-isolation tests; device
date stepping and badge checks; release class/resource/manifest inspection; G2.

### Working checkpoint
Debug app visibly changes financial date; customer release uses real financial
time. Real fixtures arrive in the next ticket, not fake financial screens here.

### Context and starting points
This is an isolated laboratory, not a release settings menu or altered device clock.
Use the distinct clock ports from SELLO-005, real Room factory from SELLO-011 and
app Root from SELLO-015. Read architecture §9,
[module map](../../development/execution-guide.md#module-and-file-map) and accepted D02/D09.

From SELLO-019 (2026-10-12): expense edit, delete and undo shipped before the sandbox
existed, so they have no sandbox regression inputs. Add them here: U01 to U05 in
`UndoCompensationContractTest` are written against the distinct clocks and can be
run as scenarios, including "advance the financial day a year, undo still ends at
six real seconds".

### Implementation plan
1. Add debug-only tools UI/bindings with separate sandbox DB/preferences namespace
   and visible session/date/zone badge; normal debug profile and production stay untouched.
2. Define owned session start/stop/switch: stop old observers/runners, wait/cancel work
   safely and invalidate old UI actions before binding the new profile/financial clock.
3. Implement freeze, +day, +month, jump and bounded play/pause through the observable
   financial clock; keep audit/monotonic sources unchanged. Persist sandbox clock/session state.
4. Require reset/replay to rewind mutated scenario history; initial arbitrary date
   selection is safe only before financial mutations. Show explicit confirmation/state.
5. Add startup/restart and release-exclusion checks. Seed/history execution is SELLO-025,
   not unchecked inserts disguised as the time-control implementation.

### Concrete cases and pitfalls
- Advance Oct 31 to Nov 1: normal month/rollover observation runs; undo expiry and
  background inactivity do not accelerate. Device wall clock remains unchanged.
- Switching profile with a pending old form cannot send its action into the new DB
  just because both generations happen to be 1; session origin is also guarded.
- A customer release must contain no tools route/controller/fixtures, even without
  shrinking. BuildConfig-hidden controls are insufficient isolation.

### Verification recipe
Create/run `*SandboxSessionTest`, `*SandboxClockPersistenceTest` and debug TimeControlsJourneyTest;
G2. Compare real audit/monotonic time before/after stepping, reopen sandbox and normal
profiles, then inspect release manifest/resources/classes for developer artifacts.
