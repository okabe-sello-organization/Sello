# SELLO-004 — Install CI, quality gates and architecture enforcement

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-003
- **Gate:** G1

### Outcome
Prevent boundary/build/test regressions from becoming normal before feature growth.
Make quality verification a required completion workflow for every subsequent task,
not a collection of optional tests. This ticket owns the enforcement tools.

### Deliverables
Host/device CI definitions, documented aggregate gate, formatting/static analysis,
composition-only import rules, dependency review and retained test/lint artifacts.
Validate the board with `board.py --check`. Keep configuration compatible with the
actual Git host; do not claim a hosted run without a remote/runner.
Deliver `scripts/verify-ticket` (target path), a versioned structured quality-report
schema, shared completion-evidence validator and tested executor/CI integration
implementing [QUALITY_FLOW.md](../../development/quality-gates.md). Extend the existing Python
board validator to verify reports, not merely the presence of an evidence field.
Document invocation, prerequisites, report retention, snapshot coverage and recovery
from failures in the execution guide. Require the host quality check/protected merge
policy with actual host/admin evidence; local hooks alone are not enforcement.

### Acceptance criteria
- Rules reject a feature importing data, domain importing Android, cross-feature
  internals, customer modules depending on catalog and debug tools in release.
- CI compiles customer release and catalog debug; tests fail the job instead of
  being swallowed. No blanket lint baseline hides new errors.
- Secrets are external; external contributions cannot access upload credentials.
- The runner derives the canonical ticket's gate without downgrade; failures,
  missing checks/devices/keys and skipped requirements return nonzero, never success.
- Review/Done transitions require a successful matching report with current input
  fingerprint. Wrong-ticket/profile, malformed, stale or unavailable evidence fails.
  Historical Done evidence remains verifiable without rerunning every predecessor.
- CI runs the same entrypoint against the merge candidate; required-check/merge
  protection is verified with the chosen host. Missing remote/admin access blocks
  hosted acceptance, not permission to mark configured YAML as executed CI.

### Tests
Demonstrate failing boundary fixtures and a failing test are caught, followed by
clean G1. Execute local gate and hosted CI when infrastructure is available; retain
reports and explicitly distinguish configured CI from executed CI.
Add runner/validator tests for absent reports, wrong IDs, insufficient gate,
nonzero checks, missing device, malformed schema and edited acceptance/code inputs.
Prove status/evidence-only edits do not stale a report, older completed tickets
remain valid, and checks can run before their own completion evidence exists.
Exercise a sample downstream ticket through run → evidence → Review; remove test
fixtures afterward without changing real ticket statuses. Verify host protection
with an authorized failing-check example; record actual results, never fabricate it.

### Working checkpoint
Document one reproducible gate used by every later ticket; app/catalog remain buildable.
An independent executor can follow the shared quality flow, and incomplete evidence
cannot pass the completion validator. Passing checks still require designated review.

### Context and starting points
Module files from SELLO-003 are the inputs; policy violations must fail before they
spread into features. Read architecture §§2/10, current Python board tests and
[verification guidance](../../development/execution-guide.md#tools-and-host-verification). No Git remote
or hosted CI is assumed available.

### Implementation plan
1. Inventory actual host/device/assembly tasks; add a documented aggregate Gradle or
   script entry point running the agreed G1 checks with retained machine-readable reports.
2. Configure formatting/Detekt/lint and architecture checks for production/debug source
   sets and module edges. Cover direct imports and qualified data references in features.
3. Add negative fixtures for prohibited edges and a deliberately failing test to
   prove checks return nonzero; fixtures must not remain compiled into customer code.
4. Configure host CI and pinned-device jobs for the chosen host; collect reports
   without exposing secrets. Device jobs may be scheduled separately but cannot vanish.
5. Add board check/tests to CI. Record which workflow actually executed; if no remote
   exists, retain local gate evidence and request runner/host access for hosted acceptance.
6. Implement the ticket-derived runner and report schema from QUALITY_FLOW.md.
   Inventory required tasks per gate; record actual execution, environment and
   input fingerprints. Avoid requiring final evidence before running the gate itself.
7. Upgrade board completion validation to consume retained reports, including
   current transition freshness versus historical Done evidence. Define artifact
   retention/retrieval and fail explicitly when reports cannot be retrieved.
8. Add negative bypass/freshness tests; update agent/runbook instructions with the
   working command. Configure and verify the chosen host's required quality check
   and merge protection with its administrator; leave acceptance incomplete until
   hosted results and protection evidence are available.

### Concrete cases and pitfalls
- A feature importing a concrete DAO through an alias/qualified name must not bypass
  the source boundary. Explain the checker coverage/limits rather than claiming complete compiler proof.
- New warnings cannot disappear into a blanket baseline; dependency review is separate
  from a lint version suggestion. PR code must never receive upload credentials.
- Static tooling belongs in the build/test path, not domain or release dependencies.
- A report for SELLO-017 cannot complete SELLO-018; G1 cannot satisfy G2. A copied
  success field is not an executed report. Changed code/acceptance requires rerun;
  adding evidence or regenerating board exports must not create a freshness loop.

### Verification recipe
Execute each negative fixture, remove it, then run the aggregate gate and board unit
tests. Archive output plus actual CI run IDs if available. Device tests require the
[device procedure](../../development/execution-guide.md#device-and-process-restoration), not test-APK assembly alone.
Run the new entrypoint for SELLO-004 itself after completed predecessor inputs,
validate its retained report, and demonstrate rejected invalid completion fixtures.
Attach local report and hosted check/protection evidence to `Quality run` and delivery
fields; request review only after required checks pass. Do not mark Done without
hosted acceptance or silently replace it with local-only verification.

### Execution progress
2026-10-08: started after SELLO-003 was committed and pushed. Owner inputs settled:
GitHub remote `Okabe94/Sello` (public, `main`) with push and admin access verified;
device tests run in CI on every pull request; ktlint 1.8.0 through ktlint-gradle
14.2.0 approved subject to a compatibility probe; Detekt deferred because the latest
stable 1.23.8 predates this toolchain and 2.0 is still alpha (to be recorded in an
ADR); architecture rules will be an in-repo script, not a library.

Part 1 of 5 delivered, uncommitted: `scripts/verify-ticket` with `scripts/quality/`
(`fingerprint.py`, `gates.py`, `runner.py`) and 29 unit tests written before the
implementation. The runner derives the gate from the canonical ticket, refuses
unknown tickets, unfinished prerequisites, downgrades, a missing toolchain or device
and the not-yet-available G3, and writes a schema-version-1 report under ignored
`build/reports/quality/`. A real G1 run for this ticket passed. G2 has only been
exercised with a simulated device.

Part 2 of 5 delivered, uncommitted: `scripts/quality/report-schema-v1.json` and
`evidence.py`, plus `board.py --check` now validating the retained report of every
Review/Done ticket from SELLO-004 onward (`--skip-evidence` for use inside a run,
`--base` to compare statuses with a merge base). Owner chose to commit final reports
under `docs/planning/mvp/quality-reports/` and keep CI artifacts as well;
`verify-ticket --retain` copies a passed report there and prints the `Quality run`
field. 60 quality-tool tests and the 32 existing board tests pass; disabling the
report hook in `board.py` makes seven of the nine end-to-end tests fail.

Part 3 of 5 delivered, uncommitted: `scripts/quality/architecture.py` with 26 tests
(module edges, module boundaries, domain purity, composition-only `:data`,
composition-is-root, feature isolation, debug tools in release; coverage and limits
are stated in the file header). Eight deliberate violations placed in the real tree
were all reported and then removed. ktlint 1.8.0 through ktlint-gradle 14.2.0 passed
its probe on Kotlin 2.4.20, AGP 9.3.3 built-in Kotlin and Gradle 9.5.0 and is applied
to all five modules with `.editorconfig` (Android Studio style, Composable naming);
existing sources were auto-formatted and two wildcard test imports made explicit. Both
checks are in the G1 profile, and a test keeps the documented gate in the board README
identical to `gates.py`. 91 quality-tool tests pass; a real G1 run passed after first
failing, correctly, on stale generated board views. Lint remains at 22 warnings.

Parts 4 and 5 delivered on branch `sello-004-quality-gates`, pull request 1:
`.github/workflows/quality.yml` runs `verify-ticket --merge-candidate BASE --gate G2`
on an API 30 emulator for the merge commit of every pull request and for `main`,
then `board.py --check --base BASE`, and uploads reports for 90 days. Actions are
pinned to commit SHAs, permissions are read-only and no secret exists. `main` is
protected. Owner chose dependency review by strict Gradle dependency locking alone
(`scripts/update-dependency-locks`) over GitHub's dependency review. ADR 0006, the
execution guide, quality flow, setup, README and agent rules describe the result,
and the owner's pull request format is the repository template.

Defect found and fixed during hosted verification: CI and the local machine computed
different fingerprints for the same commit, because `gradlew.bat` has CRLF endings
in this working tree and LF in the repository. The fingerprint now hashes files as
Git stores them; a regression test compares a working tree with a fresh clone.

Not delivered, by decision or dependency: Detekt (deferred, ADR 0006); an executable
G3 profile (SELLO-035); vulnerability and licence scanning of dependencies; locking
of Gradle plugin classpaths. The architecture checker's stated limits apply.

2026-10-08: owner approved pull request 1; moved to Done for a squash merge.
