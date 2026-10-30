# 0021 — CI is the only proof

**Owner:** project owner (user) · **Date:** 2026-10-29
**Status:** owner decision of 2026-10-29. Supersedes [ADR 0009](0009-hosted-gate-scope.md)
and the evidence and local-gate parts of [ADR 0014](0014-change-aware-quality-gates.md).
[ADR 0016](0016-release-gate-signing-split.md)'s signing split stands.

## Context

Every ticket committed proof that its checks passed: a local G1 run retained as
`quality-report.json`, a `Quality run` line in `evidence.md` binding it, input and
change fingerprints, and an acceptance hash that `tickets.py check` re-validated on
every run. The pull request's `quality` check then ran the gate again, and it alone
could block a merge. The committed copy cost a local run, a fragile freshness
contract and about 1,600 lines of tooling plus 1,500 of tests, and proved nothing
the check did not.
The check itself collected artifacts named by run attempt, so "Re-run failed jobs"
could never turn it green, and a red test was hard to find in the logs.

## Decision

- The pull request's required `quality` check is the only proof. No report, field,
  fingerprint or hash is committed or validated; `scripts/verify-ticket` is gone.
  `tickets.py check` validates structure, IDs, statuses and dependencies only.
- Documents alone get G0 on a pull request, even when they advance a ticket. Markdown
  inside a `src/` directory is code, and a document a test reads
  (`selection.HOST_INPUTS`, kept complete by a test) runs those tests.
  Python tooling and unit tests alone get the host gate. Other paths select device
  suites by the module map in `scripts/quality/selection.py`; build files, workflows,
  lockfiles, `gradle/`, the scripts the device jobs run and unknown paths select all.
- Cheap checks fail first: the planning job runs G0, and every Gradle job needs it;
  each job runs ktlint before its slower work, and device jobs build before booting.
- `quality` judges each job's result against the plan. A device job still fails when
  a selected suite leaves no fresh JUnit results or skips a test. Failing tests are
  written to the job summary and the log.
- `scripts/check` is the local loop and the command CI runs. Pushes to `main` keep
  G1 to publish trusted caches; the nightly keeps G3 on `main` with a throwaway key,
  only when `main` moved; manual runs keep G3.
- The production-signing proof of a release (ADR 0016) is now the owner's local
  `scripts/check --release` result, stated in the release pull request. Nothing in
  CI checks it: the owner merges releases (AGENTS.md, "Who merges"), and that merge
  is the acceptance.
- A ticket's `Gate` field is advisory; CI selects by changed paths. Runs on `main`
  (push, nightly, manual) get one concurrency group per commit, so none is cancelled
  or superseded by a newer one; a newer push to a pull request replaces its run.

**Amendment, 2026-10-29: the host gate runs in parallel parts.** With the screen
tests on the JVM ([ADR 0022](0022-jvm-screen-and-screenshot-tests.md)) the single
host job took 12–13 minutes on a 4-core runner, more than the device jobs, and kept
all four cores busy. It now runs as one job per part of `gates.HOST_PARTS`: `tests`
(unit tests and screenshot verification), `lint` (ktlint, then Android lint) and
`assemble` (debug and release, then the G3 release checks). Every host task is in
exactly one part and no check was dropped; `quality` judges the parts as one result
that must succeed, and `scripts/check --host` still runs them all locally. ktlint now
runs first in the `lint` part rather than before the tests: a formatting failure
cannot change a test result, and repeating it would put it on every part's path.
Measured on its own pull request (#237): the parts took 4.8 (`tests`), 6.5 (`lint`)
and 7.2 (`assemble`) minutes, so the app device job (8.9 minutes) is the critical
path again and the run took 9.3 minutes, down from 12–13.

## Consequences

- A ticket's pull request needs no local gate; contributors run focused checks and
  the device tests they wrote. The check's history on GitHub is the record; a merged
  ticket points at its pull request.
- A new test that reads a `docs/` file must list it in `selection.HOST_INPUTS`, or
  `test_selection` fails.
- The licence list is a fast check, so a dependency change without its reviewed
  listing fails on the pull request rather than the nightly.
- Tickets became one file each (ADR 0018, amended 2026-10-29); the old `evidence.md`,
  `handoff.md` and `quality-report.json` files were removed and stay in Git history.
- Tests: `test_selection` (plans per path and event), `test_check` (arguments, stage
  order, result checks), `test_junit` (failure summary) and `test_workflow` (jobs,
  judging, caches, pinned actions, nightly skip).
