# Quality gates: runner, evidence and CI

How a ticket is verified and how that verification is checked. The ticket side
(folders, statuses, definition of done, gate profiles) is in the
[tickets README](../tickets/README.md); tooling choices are in
[ADR 0006](../decisions/0006-quality-tooling.md), hosted selection in
[ADR 0009](../decisions/0009-hosted-gate-scope.md) and
[ADR 0014](../decisions/0014-change-aware-quality-gates.md). Every ticket from
SELLO-004 on follows this flow; unticketed work still runs the gates.

## Executor flow

1. Read the ticket folder, approved inputs and predecessor evidence; verify
   prerequisites before Ready.
2. Implement within owned boundaries; run meaningful focused red→green tests.
3. Update the branch with `main` once and resolve any conflict. Then run
   `./scripts/verify-ticket SELLO-017 --retain`. It reads the ticket's gate from
   `ticket.md`, executes the local requirement and writes `quality-report.json`
   into the ticket folder. The local requirement is the host gate G1 for G1 and G2
   tickets, and G3 whole for a release ticket; a G2 ticket's device suites run
   hosted, on the pull request (step 5). `--gate G2` runs every device suite
   locally too; `--affected BASE` runs the dependency-aware subset. Nothing can be
   skipped or downgraded below the local requirement.
4. Paste the printed `Quality run` line into `evidence.md`, write the other fields
   and `handoff.md`, set the status, run `python3 scripts/quality/tickets.py check`.
5. Open the final pull request with the ticket already Done. Partial PRs keep it In
   Progress and describe the remaining work. The hosted `quality` check runs the
   ticket's gate, G2 with dependency-aware device scope, on the pull request; it is
   the only run before the merge. A red run there is the ticket's own failure: fix
   it on the branch, run `--retain` again, update the field, and ask for the merge
   again.

## Done is proposed, merging approves

A pull request describes what `main` will say once merged. The ticket's final PR
carries it as **Done** with complete evidence from the moment it opens; a designated
reviewer verifies acceptance, report freshness and actual results, and nothing
reaches `main` without their go-ahead and passing required checks.

- **The approval is the merge.** The `Review` field names the PR and says its merge
  is the acceptance. It must not claim an approval, date or hosted run that has not
  happened.
- **The retained report proves the host gate; the hosted check proves the device
  suites.** `quality-report.json` binds the ticket's change set and records the G1
  run (G3 for a release). The `quality` check on the pull request is the record of
  the device suites, and it is what blocks the merge.
- **The last ticket of an epic carries the epic too**, in the same commit.
- **On the branch, Done means "proposed".** Do not start work that depends on the
  ticket from an unmerged branch.
- **Changes requested** go to the same PR. If they change the ticket's own change
  set, run the gate again and update `Quality run`; the check rejects a stale report.
- **`main` moving after the run costs nothing.** The report is bound to the PR's
  own change set, and a green PR merges as it is; branches need not be up to date.
  What lands on `main` is then verified by the push run (host gate) and by the
  nightly (G3) once `main` moved. Only a conflict GitHub reports needs a manual
  merge and `--affected origin/main --retain`.
- **Not approved or abandoned:** close the PR; `main` still shows the ticket as it was.
- After merging, the executor may start the next ticket while the hosted run on
  `main` finishes, and must fix a failure there, on the push run or on the nightly,
  before anything else merges.

## Runner and evidence contract

Standing rules for the runner, validator and CI; a change to the tooling must keep them.

- **MUST** expose one local/CI entrypoint; document requirements and actionable
  failures. Reject unknown tickets, unfinished prerequisites, unavailable required
  tasks/SDK/devices/keys, failed checks and missing results with nonzero exit.
- **MUST** report a schema version, unique run ID, ticket ID, gate, HEAD and dirty
  files, the input and change fingerprints, environment/toolchain, commands/exit
  codes/results, timestamps, warnings and artifact/device references. Never include
  personal data or secrets. A report is generated from execution, not asserted.
- **MUST** define and test two fingerprints. The input fingerprint records the whole
  tree. The change fingerprint binds completion evidence: the normalized hunks
  between the merge base with `main` and the tree, so the same edit hashes the same
  after unrelated changes land, even in the same file, and any difference in the
  edit itself, including line order, does not. Both exclude ignored outputs, the
  informative HANDOFF, and a ticket folder's bookkeeping: status lines and Execution
  progress in `ticket.md`, and `evidence.md`, `handoff.md`, `quality-report.json`
  and `artifacts/`. Acceptance edits invalidate evidence; adding evidence does not.
- **MUST** implement a shared evidence validator (`scripts/quality/evidence.py`, run by
  `tickets.py check` and CI). Reject missing, malformed, failed, wrong-ticket,
  inadequate-profile and stale reports before Review/Done. A report is stale when its
  change fingerprint is not one the candidate contains: the working tree against
  `main` locally, or, with a base, one of the first-parent steps from HEAD back to
  that base, so a batched merge group or a multi-commit push binds each PR on its
  own while the gate verifies the combined tree. Done tickets keep their historical
  snapshot; rewriting their acceptance text still fails, except for the recorded
  link-only move of 2026-10-24 (`Acceptance migrated`, see the tickets README).
- **MUST** separate execution from completion validation: a run while a ticket is In
  Progress cannot require its own final success record.
- **MUST** retain locally generated reports under ignored build reports and the
  committed `quality-report.json`; CI keeps artifacts 90 days. A missing retained
  file is missing evidence, not permission to trust a free-text field.
- **MUST** keep the required hosted check and protected merge. CI re-executes checks
  for the actual merge candidate; a committed report alone cannot authorize a merge.
  Never expose signing secrets to untrusted PRs.
- **MUST** treat distributed CI parts as incomplete execution. The collector requires
  every selected part and module, matching commit/inputs/base/event/run/attempt,
  compatible toolchains and fresh device results without skips. Schema-v2 reports
  identify full/affected/documents coverage; v1 reports stay readable.
- **SHOULD** offer focused task commands for quick feedback; they never substitute
  for the integrated gate.

## What is enforced, and by what

- **Runner:** `scripts/verify-ticket` derives the gate from `ticket.md` and exits
  nonzero on any refusal or failed check.
- **Validator:** `python3 scripts/quality/tickets.py check` validates structure,
  dependencies and statuses, then opens the retained report of every Review/Done
  ticket from SELLO-004 on and rejects a missing, malformed, failed, wrong-ticket,
  edited or stale one, or one below the local requirement (G1, or G3 for a release
  ticket). `--base <sha>` checks a candidate against it.
- **Link check:** `python3 scripts/quality/links.py` fails on any relative Markdown
  link or heading anchor that does not resolve. It is part of G0.
- **CI:** the `quality` check runs the same entrypoint and then the validator against
  the base, on every run. Eligible documentation advancing no ticket gets G0. Other
  PRs get G2 with dependency-aware device scope; shared/unknown changes get full
  devices. This hosted G2 is where a ticket's device suites execute; the local
  requirement is G1 (ADR 0014, amendment of 2026-10-24). Code pushes to main keep
  G1 for trusted cache publication; documentation pushes get G0. The nightly
  verifies `main` at full G3 with CI's throwaway signing, and only when `main` moved
  since the last successful nightly; a manual run does the same on its ref. The app
  device suite runs as two shards on two runners (`app-1`, `app-2`); the other three
  suites share one (`support`). There is no path filter; the check always reports.

These tools prove that checks ran on a known snapshot. They do not judge whether
acceptance is met; the designated review does. A committed report can be hand-edited,
which is why CI re-executes the gate instead of trusting it.

G3 adds release checks to a full G2: the licence list, the owner's production
signature and artifact checks, and installing and opening that APK on the emulator.
CI runs the same G3 with a throwaway key; only a report with the owner's production
signing completes a G3 ticket ([ADR 0016](../decisions/0016-release-gate-signing-split.md),
[release](../release/README.md)).

## Quality runner

Prerequisites: Python 3 and Git for G0. G1/G2 also need the environment from
[setup](setup.md): JDK 25 on PATH and ANDROID_HOME or sdk.dir; G2 needs an isolated
booted API-30 emulator with the default 320x640 profile. With `ANDROID_SERIAL` set the
runner asks only that device, so other attached devices, busy or not, never hold a gate;
a device that does not answer within 20 seconds is refused. Fetch the intended PR base
before selecting affected coverage.

```bash
./scripts/verify-ticket SELLO-017            # the local requirement: host gate G1 (G3 whole for a release)
./scripts/verify-ticket SELLO-017 --gate G2  # add every device suite locally; below G1 is refused
./scripts/verify-ticket SELLO-017 --retain   # local requirement -> quality-report.json
./scripts/verify-ticket SELLO-017 --affected origin/main --retain  # add the affected device suites
./scripts/verify-ticket --merge-candidate origin/main  # unticketed: G0 for documents, otherwise G1 locally
./scripts/verify-ticket --merge-candidate origin/main --gate G2  # force full G2 locally
python3 scripts/quality/tickets.py check     # completion validation
python3 scripts/quality/tickets.py status    # the board, on demand
python3 scripts/quality/links.py             # relative links and anchors
python3 scripts/quality/architecture.py      # architecture rules alone, quick feedback
./gradlew ktlintFormat                       # fix formatting findings
./scripts/update-dependency-locks            # after a deliberate dependency change
python3 -m unittest discover -s scripts/quality -p 'test_*.py'
```

Each run writes `build/reports/quality/<ticket>/<run id>/report.json` and one log per
check; that directory is ignored. CI asks `verify-ticket --ci-plan <base> --ci-event
<event>` for the exact gate, device scope and groups, executes the host and selected
device parts, then validates collection and `tickets.py check --base <base>`.
Reports are retained for 90 days: `gh run download <run id> -n quality-reports`.

For local affected scope, app selects app; catalog selects catalog; data/domain
select data/app; design-system selects design-system/app/catalog. Mixed changes
union those sets. Build scripts, lockfiles, tooling, workflows, unrecognized paths
and documentation-only ticket advancement require full device scope. Do not choose a
newer base to hide changed inputs. Schema-v2 reports and `Quality run` lines name
affected coverage explicitly.

What makes a report stale: any change to the ticket's own change set, meaning what
differs between the branch and its merge base with `main`, including code, tests,
build files, guardrail documents and the ticket's acceptance text. What does not:
`main` moving elsewhere after the run, even in a file the ticket also edited, as long
as the ticket's hunks are unchanged; the status line and Execution progress;
`evidence.md`, `handoff.md`, `quality-report.json` and `artifacts/`; HANDOFF; and
ignored build outputs. A Done ticket keeps its original snapshot; rewriting its
acceptance text still invalidates it.

Recovering from failures:

| Message | Meaning and action |
| --- | --- |
| `REFUSED: Unknown ticket` / `prerequisite ... is not Done` | Nothing ran. Fix the ID or finish the prerequisite; do not verify out of order. |
| `REFUSED: ... would be a downgrade` | The local requirement (G1, or G3 for a release ticket) is the minimum. Remove `--gate` or pass a higher one. |
| Red `quality` check on the PR | The device suites failed where the local run did not look. Read the Actions log, fix on the branch, run `--retain` again, update `Quality run`, and ask for the merge again. To reproduce locally, boot the emulator and run `--affected origin/main`. |
| Red push run or red nightly on `main` | What landed fails on its own or in combination. Nothing else merges until it is fixed: reproduce with `--merge-candidate origin/main` (add `--gate G2` on the emulator for a device failure), fix on a branch, merge, and rerun the workflow by hand from the Actions tab if the next night is too far. |
| `REFUSED: No working java` / `Android SDK not found` | Export the setup environment in this shell. |
| `REFUSED: ... no connected device` | Boot the isolated emulator from setup; test-APK assembly is not a substitute. |
| `release-production` failed: no signed release APK | G3 needs the owner's `sello.signing.*` entries; see [release](../release/README.md). |
| `FAILED: <check> exited N` | Read `<check>.log` beside the report, fix the cause, run again. |
| `FAILED: ... produced no test results` | The task ran no tests. Device results must be newer than the run. |
| `FAILED: inputs changed while checks were running` | Stop editing during a run and run again. |
| `Dependency Locking` / `does not have lock state` in a Gradle log | A dependency changed without its lock. If intended, run `./scripts/update-dependency-locks` and review the diff. |
| `Semantic Node has no parent layout with a Scroll SemanticsAction` on a device | The emulator screen is larger than the default profile and the form fits without scrolling. Use the default 320x640 AVD. |
| `Ticket validation failed: ... folder ... holds ...` | The folder name and the heading disagree. Rename the folder. |
| `report is stale` / `acceptance text changed` | The ticket's own changes or acceptance text changed after the retained run. Verify again with `--affected origin/main --retain` and update the field. |
| `does not match the recorded sha256` | The retained file or the field was edited. Regenerate both with `--retain`. |
| `BROKEN: <file>:<line>: missing target` | A document moved or a heading changed. Fix the link; the checker lists every one. |
