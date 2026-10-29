# SELLO-005 — Wire production composition and distinct time sources

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-003, SELLO-004
- **Gate:** G1

### Outcome
Make financial dates, audit time and lifetime ownership injectable without shipping debug controls.

### Deliverables
Required constructor-based Koin composition, platform-neutral observable financial
clock and zone contract, separately injected audit/monotonic timing ports, dispatchers
and owned scopes. Production clock reacts to midnight/resume/zone/time changes;
debug composition can replace only financial time. Preference contracts are neutral.

### Acceptance criteria
- Logic never calls global DI or reads wall time ad hoc; cancellation propagates.
- Financial zone is initialized once and later travel does not rewrite effective
  history. Until Room setup exists, use an explicit initialization path, not fake persistence.
- Missing required bindings fail graph construction; no successful no-op financial services.
- Financial stepping cannot change undo timers or real audit timestamps.

### Tests
Controlled-clock rollover/resume/time-zone tests, virtual-time lifecycle/cancellation
tests, and production/debug graph construction tests; run G1.

### Working checkpoint
Existing app/catalog launch under production time; pure tests exercise the future
sandbox seam without introducing its controls into release.

### Context and starting points
Legacy date-sensitive code diverged through wall-clock reads and global services.
Use the approved financial-zone decision and SELLO-003 module graph. Read architecture
§§2/4/9 and [contract checklist](../../development/execution-guide.md#contract-and-failure-checklist).
Domain clock ports are neutral; Android system observation belongs in app/platform.

### Implementation plan
1. Define financial date/zone/as-of observation, audit Instant and monotonic elapsed
   ports separately; add coroutine dispatcher/scope ownership without global lifetime lookup.
2. Implement real system adapters with explicit startup/resume/midnight/time-change
   recomputation and cancellation. Persisting financial zone is SELLO-011, not a fake default save.
3. Build app composition modules using required constructors; future workflows bind
   only as they exist. Do not require a successful dummy repository for graph creation.
4. Provide controlled ports in test/debug source sets and production graph checks;
   centralize mapping from adapters to consumer contracts.
5. Document lifecycle ownership and disposal so later sandbox session switches can
   cancel old observers and replace financial time without changing the other clocks.

### Concrete cases and pitfalls
- Month rollover while backgrounded must be observed on resume; travel must not
  change stored effective dates. Audit time and monotonic undo/inactivity remain real.
- Cancellation cannot be converted into Failed/empty financial data. A required
  missing binding is a wiring error, not a successful no-op.
- A mutable test clock must publish changes so snapshots update; changing a value
  in a field without an observable signal is not a working simulation seam.

### Verification recipe
Create/run `*FinancialClockTest`, `*ClockOwnershipTest` and composition graph tests
with controlled coroutine time. Run G1; retain midnight/resume/zone/cancellation
assertions and confirm app/catalog launch with production adapters.

### Execution progress
2026-10-09: started on branch `sello-005-composition-time` after the owner approved
Koin 4.2.2, kotlinx-coroutines 1.11.0 and lifecycle-process 2.9.4, and gave standing
permission to branch, commit, push and open the pull request for each ticket.

Delivered: clock and dispatcher ports in `:domain` (`domain.port`); production
adapters, `ProcessFinancialZone` and `ApplicationScope` in `:app` `platform`; the
composition root and `SelloApplication` in `:app` `composition`; a debug-only
`ControlledFinancialClock` with `financialTimeOverride`; architecture rules
`ad-hoc-time`, `global-coroutines` and `di-composition-only`; and
`docs/development/time-and-composition.md` for ownership and disposal. The app still
shows the greeting and the debug app runs on production time.

Decision taken during the work, for owner review: the lock-file diff showed that
`koin-android` would raise activity 1.8.0 to 1.12.4, core-ktx 1.10.1 to 1.16.0 and
lifecycle 2.9.4 to 2.10.0, and add AppCompat and Fragment. `koin-core` 4.2.2 is used
instead and the application context is bound explicitly, so none of those moved.
What did move: coroutines 1.9.0 to 1.11.0 as approved, compile-time lifecycle
artifacts from 2.8.7 to the 2.9.4 already used at runtime, and new Koin core, Stately
and test-only kotlin-reflect/kotlin-test 2.3.20 entries. `koin-android` or its
Compose artifact will be needed when ViewModels arrive and should be decided then.

`scripts/update-dependency-locks` now runs build tasks only, so a failing test or
style finding cannot block a lock refresh.

Not delivered: a preference contract. The ticket says such contracts are neutral;
none exists because nothing consumes one before SELLO-023, and `:domain` already
cannot reference DataStore. The financial zone is not persisted (SELLO-011).

2026-10-09: owner approved pull request 3; moved to Done for a squash merge. All five
tickets of this epic are Done, so the epic is Done.
