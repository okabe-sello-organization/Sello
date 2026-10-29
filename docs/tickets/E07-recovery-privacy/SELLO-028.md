# SELLO-028 — Define portable MVP backups and bounded validation

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-011, SELLO-020, SELLO-021, SELLO-023, SELLO-004
- **Gate:** G2

### Outcome
Recovery files remain valid independently of Room tables, enums and future refactors.

### Deliverables
Explicit versioned serialization DTOs/codecs, consistent financial snapshot reader,
portable preference capture, strict validation and documented format/sample files.
Include category/history/month limits, profile financial zone and stable order;
exclude jobs, permissions, secrets, simulation state and internal execution receipts.

### Acceptance criteria
- No entity serialization/class-name/enum-ordinal identity; unsupported format versions
  reject clearly. COP-only MVP records cannot silently accept other currency rows.
- Default limits are 10 MiB, 100,000 records, 10,000 characters/field and bounded
  nesting, with stricter domain field limits still enforced. Validate dates, amounts,
  references, duplicate IDs and category/budget/history consistency before mutation.
- ADR 0003/M05: apply the same 1–999,999,999,999 COP expense/income bound as manual
  entry; maximum+1 rejects the file, not just that row. Valid maximum round-trips
  exactly, while aggregate totals may exceed the per-record cap (M06).
- Financial snapshot has one revision; separate preference capture does not pretend
  Room/DataStore are one transaction. Plaintext is documented per approved policy.

### Tests
Round-trip exact values/order/zone/preferences; malformed/truncated/oversized/deep
payloads, unsupported versions, dangling references and overflow fixtures. Real-Room
snapshot consistency under concurrent writes; G2.

### Working checkpoint
Valid backup can be produced/validated programmatically without modifying source
data; no restore success or UI action is exposed before the real workflow lands.

### Context and starting points
A recovery file must outlive Room implementation changes and survive hostile input.
Use real schema/history from SELLO-011/020/021, portable preferences from SELLO-023
and approved D05/D08/D09. Read architecture §7, serialization/Room official sources
and [contract checklist](../../development/execution-guide.md#contract-and-failure-checklist).

From SELLO-019 and SELLO-021 (2026-10-12): `deleted_expense` and `deleted_income`
hold what an undo would put back. They are not financial facts and must not be
exported or imported.

### Implementation plan
1. Document the v1 envelope/field schema in `docs/data/backup-format.md`, including
   stable keys, timestamps/zone/order, currency representation, exclusions and version policy.
2. Implement independent DTOs/codecs in data/backup; capture financial rows at one
   revision and preference values separately, with explicit provenance and bounds.
3. Build read/validation staging that checks file/nesting/count/text bounds before
   deep allocation where practical, then domain/date/reference/duplicate/aggregate integrity.
4. Produce validated immutable import plan/counts independent of Room entities;
   rejection must carry actionable typed reason and cause no storage mutation.
5. Add committed synthetic valid/invalid fixtures under test resources and publish
   format contracts for document exporter/restore tickets. No legacy database translator.

### Concrete cases and pitfalls
- Round-trip H01/G01, archived categories and exact large amounts/order without
  Float, translated enum names or newly assigned history ordering.
- 10 MiB/count/nesting limits are structural caps; a shorter note violating D05
  still fails. Duplicate category IDs or dangling expense references reject the whole plan.
- Restoring execution receipts/jobs would resurrect internal operations; portable
  financial record identity is necessary, internal command execution history is not.

### Verification recipe
Create/run `./gradlew :data:testDebugUnitTest --tests '*BackupValidatorTest'` and
`*BackupRoundTripTest`, plus real-Room BackupSnapshotConsistencyTest; G2. Keep fixtures
synthetic, inspect independent expected counts/bytes/values and confirm validation never writes.
