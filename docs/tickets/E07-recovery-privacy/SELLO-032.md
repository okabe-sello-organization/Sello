# SELLO-032 — Verify privacy, file boundaries and developer-data isolation

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-029, SELLO-030, SELLO-031, SELLO-024, SELLO-004
- **Gate:** G2

### Outcome
Default platform/file/log behavior matches the local-only promise in every variant.

### Deliverables
Explicit manifest/platform backup and device-transfer exclusions, permission audit,
private staging/cache lifecycle and narrowly scoped file-sharing configuration only
where an actual consumer exists; redacted diagnostics and privacy disclosure.
Do not add a production FileProvider solely for deferred analytical exports.

### Acceptance criteria
- Unapproved OS backup/transfer is disabled/excluded; INTERNET, notification-listener,
  widget, broad storage and deferred notification permissions are absent from MVP.
- Shared diagnostic files are debug-only/synthetic or explicitly redacted; no whole
  database/private-directory provider exposure. Temporary data is cleaned safely.
- Plaintext document backup is disclosed; checksum is not called encryption.
- Verify approved D08 manual-backup responsibility, cloud-provider/external-copy
  disclosures and P01–P04 boundaries with actual merged manifests and file journeys,
  not an approval record mistaken for implemented privacy protection.
- Release resources/classes contain no fixtures, clock controllers or tools route;
  normal debug profile, sandbox and production cannot cross storage boundaries.

### Tests
Merged-manifest/resource/dependency assertions, malformed URI/path and cache cleanup
tests, device permission/file flow review and log inspection with synthetic sensitive
markers. Run G2 and retain artifact inspection output.

### Working checkpoint
Real backup/restore/reset continues working without broad permissions, exposed
private files or developer artifacts in the customer package.

### Context and starting points
Generated Android backup defaults were unsafe for the local-only promise; SELLO-011
must already disable them before storing money. This ticket audits the complete
file/recovery/variant boundary, using SELLO-024/029–031. Read architecture §7,
approved D08/D09 and official backup/documents sources in the executor guide.

### Implementation plan
1. Inspect merged debug/release manifests and extraction rules, not only main XML;
   verify cloud/device-transfer exclusions and absence of unsupported permissions/services.
2. Inventory documents/private staging/cache/logs and every actual share consumer.
   Permit only narrow owned temporary paths; do not add unused production FileProvider.
3. Test URI/path/provider failure and cleanup/retention boundaries; separate personal
   normal-debug profile from synthetic sandbox output and customer storage.
4. Inspect release compile/runtime dependencies/resources/classes with and without
   shrink assistance so obfuscation cannot masquerade as debug-source isolation.
5. Write `docs/privacy/mvp-data-handling.md` describing exact stored/shared data,
   plaintext disclosure, platform exclusions and diagnostic redaction; fix observed gaps.

### Concrete cases and pitfalls
- A provider pointing at whole app private storage is a leak even if the UI only
  shares one file. Logs must not contain synthetic sensitive markers used in tests.
- INTERNET/notification-listener/widget/broad-storage permissions remain absent;
  opening a system document does not justify broad filesystem access.
- Old staging cleanup must not delete unrelated external user documents or another
  sandbox/profile's pending recovery file.
- `sello-unreadable.db` (SELLO-045, [ADR 0008](../../decisions/0008-unreadable-storage-start-over.md))
  is private plaintext financial data: verify it is never shared, backed up by the
  system or left behind by reset.

### Verification recipe
Create/run ManifestPrivacyTest, FileBoundaryTest and DebugReleaseIsolationTest;
G2. Retain merged-manifest/artifact inventories, synthetic-marker log checks and
executed real file/restore/reset journeys under minimal permissions.
