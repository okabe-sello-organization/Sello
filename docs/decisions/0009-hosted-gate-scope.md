# 0009 — What the hosted check runs, and when

**Owner:** project owner (user) · **Updated:** 2026-10-22
**Status:** approved hosted gate policy, amended by [ADR 0014](0014-change-aware-quality-gates.md)
and, on 2026-10-24, by the merge queue decision below (queue live the same day).

The required `quality` check always reports; the workflow has no path filter.
`scripts/verify-ticket` owns selection, execution and evidence:

| Event | Gate |
| --- | --- |
| Pull request advancing no ticket, documentation only | G0, Python/Git only |
| Other pull requests | G2, with dependency-aware device scope; uncertain/shared inputs use full G2 |
| Merge queue entry (`merge_group`) | The same candidate gate as a pull request, from the group's base, on the real merge result |
| Documentation-only push to main advancing no ticket | G0 |
| Code/other push to main | G1, including builds that publish trusted main caches |
| Nightly or manually requested hosted run | Full G2 on main |

G0 covers board structure/tests, quality-tool tests and architecture. It does not
execute Android/Gradle checks and cannot be ticket completion evidence. G1 retains
formatting, all host tests, lint and debug/release assembly. G2 retains all of G1
and runs the selected API-30 module suites. G3 is still unavailable until its
release checks exist; no lower profile can replace it. Completion evidence is
validated with `tickets.py check --base` on every hosted run.

## Documentation and module selection

Documentation is recognized non-executable content under docs (Markdown, text,
HTML/CSS, images, CSV/JSON), or top-level Markdown. Python, shell, workflows,
build/lock files, module contents and unknown paths do not qualify for G0. Count
deletions, uncommitted/untracked paths and both ends of moves. Ticket advancement
(In Progress/Review/newly Done) prevents the documentation exception.

Known module source paths and data schemas select device consumers:

| Changed module | Device suites required |
| --- | --- |
| app | app |
| catalog | catalog |
| data or domain | data, app |
| design-system | design-system, app, catalog |

Union the scopes for mixed changes. Shared/build/toolchain/workflow/tooling or
unknown paths select all four suites. The graph has an automated check against
Gradle's modules/dependencies; new graph edges require review. Documentation-only
ticket advancement selects full devices. Host tasks remain complete in every G1/G2.

## Local parity and evidence

`verify-ticket <ticket>` runs the local requirement: the host gate G1, or G3 whole
for a release ticket (ADR 0014, amendment of 2026-10-24; before it the local default
was the full ticket gate). The hosted G2 above is where a ticket's device suites
execute. Add `--affected BASE` for dependency-aware device selection locally;
choose/fetch the real PR base, not a later commit hiding changed inputs. `--gate G2`
always forces all four device suites. The candidate command `--merge-candidate BASE`
derives G0 for documents and otherwise runs G1 locally; a higher explicit gate is
allowed. A G3 ticket still needs the owner's whole G3 run.

New schema-v2 reports name full/affected/documents, base, changed paths and exact
selected modules. Each CI part is bound to the same selection, revision, inputs,
event and run/attempt. Only selected groups are scheduled; every selected module
must produce fresh passing results without skips. Unselected modules are not
reported as executed. Historical schema-v1 full reports remain valid evidence.

## Conditions and post-merge duplication

The lighter push check relies on main taking candidates only through the merge
queue, which runs this check on the real merge result right before merging, enforced
for administrators too, with squash merges of the checked candidate. Decision of
2026-10-24: with several open pull requests, every merge invalidated every other
branch and forced a rebase, a local rerun and a hosted rerun, growing with the
square of the number of open pull requests. The workflow verifies `merge_group`
entries, and ruleset 25780776 on `main` requires the quality check, squash merges,
pull requests and the merge queue (squash, all-green grouping, up to five entries
per group). GitHub offers merge queues only in organization-owned repositories, so
on 2026-10-24 the repository moved from the owner's personal account to the
`okabe-sello-organization` organization; the old URL redirects. The classic
"require branches to be up to date" rule is off: the queue builds the real merge
result and runs this check on it right before merging. If the queue is ever
disabled, restore the up-to-date requirement or device checks on main pushes.
Nightly runs cover the full device graph even when PRs use a narrower scope.

Code pushes deliberately keep G1: PR caches are read-only, so trusted main builds
must publish new outputs. We do not import untrusted PR caches or pretend a prior
PR report is fresh execution on main. The post-merge reduction applies to eligible
documentation only. Changed tested inputs require fresh completion evidence.

Regression coverage lives in `scripts/quality/test_selection.py`,
`test_candidate_gate.py`, `test_distributed.py`, `test_workflow.py` and evidence
validation tests. PR/merge permissions remain owned by AGENTS.md.
