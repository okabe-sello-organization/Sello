# SELLO-039 — Persist carryover configuration and atomic correction replay

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-038, SELLO-011, SELLO-012, SELLO-013, SELLO-004
- **Gate:** G2

### Outcome
Make approved carryover settings and derived availability reliable across restart,
concurrent edits and cancellation without altering historical base-budget facts.

### Deliverables
Reviewed Room migration/exported schemas, validated configuration/transition commands,
real transactional adapters and revision-consistent carryover reads using the domain
policy. Document whether derivation is on demand or cached and how stale caches are fenced.

### Acceptance criteria
- Upgrade retains all MVP history/settings/receipts; existing users stay in approved
  no-carry mode unless explicit opt-in policy says otherwise. No destructive fallback.
- Commands use operation ID, generation and expected version; setting changes,
  affected financial revisions and durable receipts are atomic.
- Correction replay/invalidation cannot publish mixed revisions or partially update
  a chain. If bounded resumable work is needed, old/new visibility is explicitly safe.
- Identical replays return receipts; stale commands, reset generation, invalid transitions
  and overflow reject without partial mutation. Cancellation does not imply rollback.
- No clock-triggered money generation or duplicate month-boundary writes; reads work
  after app absence. Customer activation remains unavailable until recovery/UI tickets ship.

### Tests
Real-Room old-schema migration/retention and command/replay tests: duplicate submits,
rollback, concurrent historical edit/read, invalid versions, reset fencing, cancellation
around commit and DB reopen. Assert rows/revisions/receipts, not mocked DAO calls; G2.

### Working checkpoint
Upgraded app launches with prior no-carry data intact; data/domain integrations work
without exposing an opt-in that has incomplete backup or customer UX.

### Context and starting points
Inspect actual schema/migrations and operation protocol from SELLO-011–013, domain
contracts from SELLO-038 and accepted policy from SELLO-037. Review portable-format
impact with SELLO-040 before changing persistence; do not serialize Room entities.

### Implementation plan
1. Choose the minimal schema/index/caching changes from measured replay/query needs;
   document migration and portable-format impacts before implementation.
2. Add old-schema retention and atomic failure tests; implement non-destructive migration.
3. Implement settings/transition commands with the existing receipt/version/generation
   protocol and real-Room concurrency guarantees.
4. Wire consistent reads and correction replay to the domain calculator; expose typed
   pending/failed states where required, never stale data labeled current.
5. Exercise restart/cancellation/rollback/correction races and existing no-carry commands;
   run G2 and publish storage/read contracts for recovery and UI consumers.

### Concrete cases and pitfalls
- A current default edit cannot overwrite prior base limits; an approved historical
  correction may affect later derived carry only through the reviewed replay policy.
- A follow-up failure after commit cannot remove the settings change or financial receipt.
- Adding every future month as a persisted job is not required for calendar continuity.

### Verification recipe
Run real-Room `CarryoverMigrationTest` and `CarryoverCommandContractTest` (target names),
existing financial/recovery suites, connected affected tests and the complete G2 runner.
