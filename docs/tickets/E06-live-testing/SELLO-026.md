# SELLO-026 — Add truthful failure, receipt and recovery diagnostics

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-019, SELLO-025, SELLO-004
- **Gate:** G2

### Outcome
Debug exploration can reproduce unsafe-looking edges without creating another financial implementation.

### Deliverables
Debug-only scripted adapter faults for read/command boundary and caller interruption,
receipt/status inspector and redacted synthetic diagnostic export. Use narrowly
scoped composition decorators; keep actual Room commit/rollback tests separate.

### Acceptance criteria
- Simulated read failure shows ErrorSlip/last-good data, not zero; interruption after
  commit recovers original receipt and never invites a blind new operation.
- Scenarios include duplicate submit, stale edit, expiry/undo conflict and overflow
  rejection. Each fault has an explicit scope/trigger and can be disabled/reset.
- Fake-port failure is never described as proof of SQL rollback. Neither injection
  classes nor diagnostic menus/data ship in release, even with code shrinking disabled.
- Export is synthetic/redacted and explicit; no raw user fields or secrets in logs.

### Tests
Debug decorator contract/recovery tests, real-Room corroborating transaction tests,
device fault/reset/receipt checks and release artifact exclusion assertions; G2.

### Working checkpoint
Normal sandbox behavior resumes after clearing a fault; real app workflows do not
silently inherit failure injection or modified arithmetic.

### Context and starting points
Investigating “did it save?” requires truthful receipts, not debug wrappers that
invent financial outcomes. Use command/undo ports and runner from SELLO-019/025.
Read architecture §§5/9 and
[failure checklist](../../development/execution-guide.md#contract-and-failure-checklist).

### Implementation plan
1. Add narrowly scoped debug composition decorators for next-read failure, caller
   interruption and post-commit delivery failure; define trigger scope/count/reset explicitly.
2. Provide read-only operation/receipt/revision inspector showing original status and
   simulated fault source. Never rewrite a committed receipt to make the UI look failed.
3. Add duplicate, stale editor, overflow and undo-conflict scripts using actual
   commands; transaction rollback evidence still comes from real-Room constraint tests.
4. Pause scenario progress on unresolved operations and offer recovery by saved ID;
   clear/reset faults predictably and prevent them leaking across sessions.
5. Export only synthetic/redacted diagnostics through a debug-only narrow file adapter;
   document payload/cleanup and verify classes/resources/dependencies absent in release.

### Concrete cases and pitfalls
- Failure delivered after a real 10,000 commit must still inspect one committed
  expense; recovery returns its receipt instead of creating another row.
- Next-read error with last-good 30,000 shows explicit stale/error, not a zero graph.
- An adapter exception before calling Room does not prove Room rollback; inspector
  must distinguish simulated boundary failures from actual transaction outcomes.

### Verification recipe
Create/run `*DebugFaultDecoratorTest`, ReceiptRecoveryDiagnosticJourneyTest and matching
real-Room rollback/recovery tests; G2. Verify triggering once, clearing, switching
sessions and exporting redacted output; inspect customer artifact for absence again.
