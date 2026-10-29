# SELLO-036 — Accept the complete MVP and prepare release handoff

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-033, SELLO-034, SELLO-035, SELLO-004
- **Gate:** G3

### Outcome
Make a recorded go/no-go decision from executed evidence, not epic/ticket counts alone.

### Deliverables
Release checklist with artifact hash/version/signing identity, accepted MVP scope,
full test/device/performance/privacy evidence, user recovery guidance, known issues
and next-increment backlog. Final board statuses reflect reviewed delivery evidence.

### Acceptance criteria
- Execute fresh install → category/budget → expense → receipt/Recibo → edit/undo →
  income → Resumen → appearance/order → backup → mutate → restore → restart → reset →
  reuse, on the signed production candidate. No skipped step is an implicit pass.
- Independently launch catalog groups/themes/states and debug sandbox time/history/
  recovery scenarios; verify neither ships inside the customer artifact.
- All other 35 tickets' acceptance/gates are satisfied; any genuine blocker prevents
  release. Remaining nonblocking issues are specifically owner-approved, not hidden.
- No Patrimonio, recurrence/batch/rates/alerts/widgets/capture or unsupported links
  are present. Post-MVP scope is documented separately, without prebuilt dormant modules.

### Tests
Full G3 plus documented product-owner walkthrough and fresh board/link/export
validation. Record exact release revision and distinguish manual/device tests from assembly.

### Working checkpoint
MVP is demonstrably installable, useful, recoverable and ready for the agreed
distribution channel; do not publish/commit/tag automatically without explicit approval.
The approved channel is private APK only. Handoff must identify the exact APK hash,
version/signing identity and installation/recovery instructions; no store listing or
AAB upload is a prerequisite. Signing custody and device/reviewer owners must be resolved.
Approved D10 assigns product/release go/no-go to the project owner; independent
technical review and actual physical-device/signing evidence must be recorded before
acceptance. Approval of the process is not approval of a future candidate artifact.

### Context and starting points
This is the final go/no-go, not a last-minute implementation bundle. Inputs are
reviewed evidence from all prior tickets, signed artifact from SELLO-035, accepted
MVP contract and product approval owner. Read architecture §1/10,
[workflow](../README.md#closing-a-ticket) and delivery-record guide.

### Implementation plan
1. Create `docs/release/mvp-checklist.md` listing exact candidate revision/hash/version/
   identity, supported device/API/window matrix, data/recovery assumptions and report links.
2. Verify the other 35 tickets are Done with actual accepted evidence; inspect
   deferred-feature absence and every visible action/route before the owner walkthrough.
3. Execute the full signed-customer flow from empty through save/edit/undo/income/
   summary/preferences/backup/restore/restart/reset/reuse, comparing known exact values.
4. Separately launch independent catalog and debug sandbox; verify scenarios/types
   are useful and not packaged into customer release. Record any actual blockers.
5. Obtain explicit product go/no-go, document nonblocking issue ownership and
   user recovery/support instructions. Mark this ticket Done only after acceptance;
   leave distribution/upload/tag/commit to a separately authorized action.

### Concrete cases and pitfalls
- “36/36 Done” is the resulting state after this task, not its circular precondition.
- Missing device/signing/product review cannot be replaced by a unit-test count.
  An unresolved recovery or unsafe money defect blocks release, even if build is green.
- No inactive Patrimonio/recurrence/export/widget/capture link remains; catalog/debug
  apps are explicitly separate developer artifacts, not installed customer features.

### Verification recipe
Run complete G3, `python3 docs/planning/mvp/board.py --check` and board unit tests.
Use the final checklist for actual owner/device walkthrough and record approval,
candidate hash, exact scenario results and retained warnings. Generate board/CSV only
after evidence/status edits; do not publish automatically.

### Execution progress

- **Owner decisions, 2026-10-23:** the owner is the independent technical reviewer and
  gives the product go/no-go; the walkthrough runs on their Samsung SM-S731B
  (`R5GYC4FA50N`) with a fresh install and synthetic data (option b); SELLO-075–077 and
  the missing licence notice are accepted as non-blocking; the version name is
  `0.1.0`; the backup blocker below is fixed in this ticket and its misleading message
  logged as SELLO-078.
- **Blocker found and fixed:** on the phone every backup failed its own check because
  Android 16's clock gave the backup timestamp microseconds; `BackupExport` now rounds
  it to the millisecond (red first in `BackupSnapshotConsistencyTest`). The walkthrough
  was repeated on the rebuilt candidate from a fresh install.
- **Delivered:** [MVP checklist](../../release/mvp-checklist.md) with the candidate,
  scope, evidence, walkthrough, developer-app check, accepted issues and recovery
  guidance.
