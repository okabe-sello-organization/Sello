# SELLO-040 — Extend portable backup, restore and reset for carryover

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-039, SELLO-028, SELLO-029, SELLO-030, SELLO-031, SELLO-004
- **Gate:** G2

### Outcome
Carryover-enabled financial histories can be recovered portably before customer
activation; older MVP backups remain interpretable without inventing missing carry.

### Deliverables
Versioned portable-format evolution and compatibility documentation, strict codecs,
restoration/reset integration and synthetic old/new backup fixtures. Record whether
derived values are omitted or included with validated provenance; never trust caches.

### Acceptance criteria
- Review old-version compatibility and unknown-version rejection explicitly; old
  backups restore with approved no-carry interpretation, not guessed starting deficits.
- Round trip preserves actual records, base history and the approved carry configuration;
  replay matches reviewed values without counting carry as new transactions/income.
- Validate references, bounds, transitions and signed semantics before confirmation.
  Invalid files cause no financial/settings mutation or partial historical replay.
- Replacement advances generation, fences old commands/undo and recovers its receipt;
  settings completion remains resumable. Reset removes carry settings/history correctly.
- Disclosure/cleanup/privacy guarantees remain intact; no fixtures contain personal data.
- Before SELLO-041's customer presentation is integrated, enabled-policy restore is
  exercised only through isolated test/developer paths. The customer app explicitly
  rejects not-yet-supported enabled-policy files before mutation rather than restoring
  carry-enabled data into a UI that cannot explain it. No false successful restore.

### Tests
Strict codec and real-Room replacement/reopen tests, old/new round trips, corrupt
configuration/overflow/references, unknown versions, generation fencing and preference
failure recovery. Re-run MVP backup/restore/reset journeys and G2.

### Working checkpoint
Both existing and carry-capable backups restore safely; released no-carry recovery
works unchanged, and customer carryover activation still waits for SELLO-041.

### Context and starting points
Read implemented portable spec/codecs and restore/reset protocol from SELLO-028–031,
SELLO-039 migration/commands and SELLO-037 reviewed example corpus. Architecture §7
owns bounds, plaintext disclosure and recovery guarantees.

### Implementation plan
1. Inventory actual wire versions and history/configuration fields needed to replay;
   approve compatibility changes and update format documentation.
2. Add failing synthetic compatibility/validation/round-trip tests; implement strict
   decoding without silently upgrading unknown financial meaning.
3. Integrate staged restore validation, atomic replacement and domain replay; preserve
   receipt recovery and settings follow-up behavior across cancellation/restart.
4. Extend reset/old-command fencing and preview counts/provenance without exposing
   internal caches or operation IDs as portable replay authority.
   Keep customer activation/restore support unavailable until SELLO-041 integration;
   verify rejected unsupported activation makes no financial/settings changes.
5. Execute old/new backup, restore and reset regressions on real storage/devices;
   run G2 and publish recovery evidence before customer activation.

### Concrete cases and pitfalls
- A negative carried deficit is not an invalid positive expense; money validation
  must respect distinct types while rejecting malformed original fields.
- Exporting only today's effective capacity loses the evidence needed for corrections.
- A successful financial replacement with unfinished settings remains incomplete restore.

### Verification recipe
Run targeted carryover codec/restore tests and original restore/reset connected suites,
then G2. Retain synthetic file versions, exact expected replay totals and reopen evidence.
