# SELLO-029 — Deliver real document-picker backup creation

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-028, SELLO-015, SELLO-004
- **Gate:** G2

### Outcome
A user can save a usable recovery file and distinguish success from canceled/failed output.

### Deliverables
Backup screen accessible from settings, Android document port/adapter, bounded
off-main serialization/write/close and progress/error/last-success presentation.
Use an approved Sello filename and accessible file/date/size description.

### Acceptance criteria
- “Guardado” and last-success time update only after successful write/flush/close;
  picker cancellation is not success or data loss. Provider/write/close faults are visible.
- UI does not freeze on large valid data; no broad storage permission or arbitrary
  shared-path write. Retrying file creation cannot mutate financial records.
- Process recreation restores workflow state honestly; an uncertain file outcome
  is not stamped successful. Backup contents exclude sandbox metadata/internal jobs.
- Approved D08/P01–P04 disclosure precedes destination/save: plaintext financial
  contents, no password protection, manual-recovery responsibility and provider-managed
  sync for cloud-backed locations. Explain that app reset cannot erase external copies.

### Tests
Document-port cancellation/write/close fault tests and device picker save→read→validate
journey; large-file responsiveness and active-write snapshot tests. Catalog backup
feedback states, plus G2.
Cover accessible disclosure/cancel/save flows, no encryption claims and temporary
cleanup without deleting user/provider-owned copies; final copy needs UI review.

### Working checkpoint
User-generated backup parses with SELLO-028 and matches actual financial history;
all manual workflows remain available. Restore action stays absent until SELLO-030.

### Context and starting points
Returning a document URI is not a successful backup. Use SELLO-028 encoder/snapshot
and app platform/Root contracts; read
the [Sello v2 components](../../design/v2/components.html) and, until v2 designs it, the v1.1 [backup screen](../../design/sello-spec.html#s-respaldo), approved D08 and official
document-picker sources in the guide. Files may be plaintext and provider failures are real.

### Implementation plan
1. Define app-owned document create/write port with typed canceled/failed/completed
   outcomes; Android adapter uses the system create-document flow and URI permissions.
2. Add backup feature states for idle/picker/preparing/writing/error/completed using
   bounded off-main serialization and owned cancellation; choose approved Sello filename.
3. Record last-success metadata only after stream write/flush/close completes. Distinguish
   provider/caller/file failures from any unrelated financial mutation.
4. Wire actual settings backup route and status/copy/accessibility; do not expose
   an unimplemented restore button. Handle recreation without blindly starting another file.
5. Clean owned private staging/cache and disclose partial external-file possibility;
   never delete arbitrary documents or claim atomicity across Room/provider/DataStore.

### Concrete cases and pitfalls
- Cancel picker: no success stamp/date change. Fail on close after some bytes: failed
  file outcome, unchanged money and last-success value.
- Create file then kill process: pending/unknown completion must not display Guardado
  just because a URI exists. Recovery may require verifying/creating a new output file.
- A successful G01 backup must revalidate to three expenses/one income and exact
  category/limit history; progress UI must not block main-thread input.

### Verification recipe
Create/run `*BackupExportViewModelTest`, `*DocumentWriteContractTest` and BackupFileJourneyTest;
G2. Select a real provider/document, reopen/validate resulting bytes, inject write/close/
cancellation failures and record exact file size/count/hash plus last-success behavior.
