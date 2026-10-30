# SELLO-043 — Verify and release the complete signed carryover increment

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-042, SELLO-033, SELLO-034, SELLO-035, SELLO-036, SELLO-004
- **Gate:** G3

### Outcome
Ship an independently reviewed, recoverable carryover increment without regressing
released MVP behavior or misrepresenting financial availability.

### Deliverables
Executed integrated acceptance matrix, migration/portable-recovery evidence,
approved performance comparison, production-signed optimized artifact/install tests,
release notes explaining opt-in/deficits/corrections and designated release approval.

### Acceptance criteria
- Execute approved no-carry and carryover journeys across the accepted API/device/
  window/font/theme/input matrix; actual process-death recovery is not just rotation.
- Upgrade representative prior release databases and restore old/new backups; verify
  signed-chain totals and record retention, including negative capacity and corrections.
- Measure large-history/replay responsiveness against agreed budgets; no unbounded
  main-thread work or invisible stale-revision success. Review deviations, not blanket baselines.
- Inspect customer artifact for developer tooling/fixtures/test clocks; signing,
  privacy, dependency and shrinker checks pass with actual required credentials/device.
- Product/financial/design/technical release reviewers accept evidence; no Jira import,
  hosted run or independent approval is claimed without actual execution.

### Tests
G3 including existing MVP regression/recovery suites, signed-chain corpus, device
accessibility/restoration, retained CI reports, optimized signed installation and
large-history replay benchmarks on the approved matrix.

### Working checkpoint
Approved complete carryover release is installable/recoverable; original non-carry
daily-use journeys remain usable. Every epic child has actual Done evidence.

### Context and starting points
Use completed SELLO-037–042 artifacts and MVP SELLO-033–036 release runbooks.
Inspect actual performance targets, signing procedures and support matrix rather
than copying outdated baseline devices/tasks. No host/device/key availability is assumed.

### Implementation plan
1. Map approved requirements/examples to executed release journeys and reviewers;
   validate all predecessor Quality run and decision evidence.
2. Run integrated historical/signed-chain/transition/no-carry and old/new recovery
   cases on the accepted matrix; distinguish assembly from executed device results.
3. Measure replay/correction/large-history costs against accepted budgets; fix scoped
   carryover regressions with meaningful red→green tests and rerun affected gates.
4. Build/install and inspect production-signed optimized artifact; verify developer
   exclusion, privacy and old-release upgrade/recovery with actual credentials.
5. Run final G3, retain snapshot-bound reports and obtain independent/product release
   approvals; update release notes and epic evidence only after actual acceptance.

### Concrete cases and pitfalls
- Correct arithmetic in a unit test does not prove migration, portable restore or UI
  consistency after process death. A release with debug controls hidden still fails isolation.
- Local runner success is not a hosted required check; debug signing is not customer-distribution signing.
- A missing reviewer/device/signing key is an incomplete gate, not a reason to call Done.

### Verification recipe
Use the final G3 ticket runner and current release runbook; record exact candidate
revision/dirty inputs, artifact/device IDs, counts, failures/warnings, retained reports
and named approvals. Update canonical statuses and regenerate/check both boards.
