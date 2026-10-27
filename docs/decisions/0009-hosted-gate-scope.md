# 0009 — What the hosted check runs, and when

**Owner:** project owner (user) · **Updated:** 2026-10-26
**Status:** approved hosted gate policy, amended by [ADR 0014](0014-change-aware-quality-gates.md),
on 2026-10-24 by the merge queue decision below (queue live the same day), and on
2026-10-26 by the amendment at the end, which retired the queue.

The required `quality` check always reports; the workflow has no path filter.
`scripts/verify-ticket` owns selection, execution and evidence:

| Event | Gate |
| --- | --- |
| Pull request advancing no ticket, documentation only | G0, Python/Git only |
| Other pull requests | G2, with dependency-aware device scope; uncertain/shared inputs use full G2 |
| Documentation-only push to main advancing no ticket | G0 |
| Code/other push to main | G1, including builds that publish trusted main caches |
| Nightly hosted run | Full G3 on main, with CI's throwaway signing, only when main moved since the last successful nightly |
| Manually requested hosted run | Full G3 on the requested ref |

G0 covers board structure/tests, quality-tool tests and architecture. It does not
execute Android/Gradle checks and cannot be ticket completion evidence. G1 retains
formatting, all host tests, lint and debug/release assembly. G2 retains all of G1
and runs the selected API-30 module suites. G3 adds the release checks of
[ADR 0016](0016-release-gate-signing-split.md), signed on CI with a throwaway key;
no lower profile can replace it. Completion evidence is validated with
`tickets.py check --base` on every hosted run.

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

The merge queue described in this section ran from 2026-10-24 to 2026-10-26; the
amendment below replaces it. The lighter push check relied on main taking candidates only through the merge
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

## Amendment 2026-10-26 — a green pull request lands as it is; the nightly verifies main

**Owner:** project owner (user) · **Status:** user-authorized implementation.

Measured on 2026-10-26 over the last forty hosted runs, one merge cost three runs:
the pull request check (11 to 16 minutes), the merge queue check on the real merge
result (11 to 15 minutes) and the push run on main (6 to 10 minutes). With one pull
request open at a time, which is how merges went, the queue repeated the pull
request's device suites on the same tree.

From this amendment:

- A pull request's `quality` check is the only run before the merge. It keeps G2
  with dependency-aware device scope. A green pull request merges at once, by hand
  or with auto-merge, even while another pull request's check is still running.
  Branches still need no update unless GitHub reports a conflict.
- The merge queue rule leaves ruleset 25780776 and the workflow no longer listens to
  `merge_group`; the planner refuses that event. Squash stays the only merge method;
  the quality check, pull requests and no force pushes stay required, and "require
  branches to be up to date" stays off.
- The push to `main` keeps G1. It still publishes the trusted Gradle caches, and it
  catches a compile or host-test collision between two merges within minutes.
- The nightly (07:23 UTC) verifies the whole of `main` at G3: the host gate, the
  licence table, a release build signed with a throwaway key, and every device
  suite. Before anything runs, the planning job asks the Actions API for the head of
  the last successful nightly and skips every job when `main` is still there; that
  job alone holds `actions: read`. A manual run verifies its ref the same way.
- A red run on `main`, from a push or from the nightly, blocks the next merge until
  it is fixed on `main`, the rule AGENTS.md already applied to the push run.

What is accepted: two pull requests that are each green alone are never tested
together before they land. The push run catches most such collisions within ten
minutes; a device-only one waits for the nightly. If that window ever bites, the
queue can return: add `merge_group` back to the workflow trigger and the planner's
events, and the rule back to the ruleset.

Verification: `test_distributed` covers the G3 plan for nightly and manual runs and
the refused event; `test_workflow` covers the missing trigger, the skip step
executed against a fake `gh`, its single extra permission and the job conditions.
The skip on a real schedule is proven by the first nightly after the merge.
