# Sello tickets

Every piece of planned work is a ticket, and every ticket lives in one folder here.
Scope authority stays with [ARCHITECTURE.md](../../ARCHITECTURE.md) and
[ADR 0001](../decisions/0001-mvp-contract.md); this page says how work is tracked,
verified and closed. Ticket IDs are stable local references. No hosted tracker exists.

```text
docs/tickets/
  README.md                   this page
  TEMPLATE/                   copy for a new epic or ticket
  E15-release-fixes/
    README.md                 the epic: status, goal, exit, owner decisions
    SELLO-078/
      ticket.md               the canonical ticket, the only file the tools parse
      evidence.md             delivery notes (older tickets; nothing reads it)
      handoff.md              what changed, where, what is still open
      artifacts/              screenshots, walk notes, reviews
```

Nothing generated is committed. A status view is printed on demand and never
conflicts:

```bash
python3 scripts/quality/tickets.py status        # every epic
python3 scripts/quality/tickets.py status E17    # one epic
python3 scripts/quality/tickets.py check         # structure, IDs, statuses, dependencies
python3 scripts/quality/links.py                 # every relative Markdown link and anchor
```

## The files

- **`ticket.md`** starts with `# SELLO-0NN — Title`, then `- **Type:**`, `- **Priority:**`,
  `- **Status:**`, `- **Depends on:**` and `- **Gate:**`, then the required sections:
  Outcome, Deliverables, Acceptance criteria, Tests, Working checkpoint, Context and
  starting points, Implementation plan, Concrete cases and pitfalls, Verification
  recipe. `### Execution progress` may follow. Everything except the status line and
  Execution progress is the acceptance text. The folder name must equal the ticket ID.
- **`evidence.md`** holds delivery notes as `- **Field:** text` lines (below). No tool
  reads it; the pull request's `quality` check is the proof that checks passed
  ([ADR 0021](../decisions/0021-ci-is-the-only-proof.md)). Older folders may also hold
  a `quality-report.json`, kept as history.
- **`handoff.md`**: three to six lines for the next person. Required for Done.
- **`artifacts/`**: images, videos and notes the evidence refers to. Optional.
- **The epic `README.md`** starts with `# SELLO-E0N — Title`, then `- **Status:**`,
  `- **Increment:**` (`MVP` or `Post-MVP`; explicit from E09 on), `- **Goal:**` and
  `- **Exit:**`, then owner decisions. It carries no per-ticket lines, so two tickets
  in one epic never edit the same file. The epic's status changes once, when its last
  ticket closes.

Changing a ticket means editing its own folder and nothing shared. Adding a ticket
means adding a folder from the template. Only the PR that closes an epic edits the
epic README and refreshes [HANDOFF](../development/HANDOFF.md).

## Statuses and flow

`Backlog → Ready → In Progress → Done`; `Blocked` is an explicit detour and `Review`
is for work waiting on something other than its merge.

- **Backlog:** scoped, dependencies or decisions not yet satisfied. **Ready:**
  dependencies Done, acceptance understood, decisions approved, devices known.
- **In Progress:** one owned ticket; red→green evidence starts here. **Done:**
  acceptance, tests, the gate and review all passed; written code is not Done.
- **Blocked:** record blocker, owner, affected IDs and next check; keep the status
  honest about missing device or signing evidence.
- One ticket in progress per contributor. Dependency links, not epic numbering, give
  the order. Do not parallelize overlapping schemas, composition wiring or policies.
- Reopen Done only with a stated regression; never silently weaken acceptance.

**Done is proposed, merging approves.** The final pull request carries the ticket as
Done with complete evidence from the moment it opens; the `Review` field says the
merge is the approval. Until merged it is a request. Earlier PRs of the same ticket
keep it In Progress and say what remains. Closing the last ticket of an epic marks
the epic Done in the same commit. Changes requested go to the same PR, and the
`quality` check runs again on every push. What CI runs is in the
[quality gates guide](../development/quality-gates.md). Update a branch from `main`
only when GitHub reports a conflict.

## Definition of done — every ticket

1. Deliverables are checked in and satisfy every acceptance criterion within the
   architecture's boundaries, without activating deferred features.
2. Behavior changes have a meaningful failing test first, then passing evidence;
   build or documentation tasks show negative/configuration/link checks instead.
3. The pull request's `quality` check passes. A skipped gate is an outstanding
   requirement, not a pass.
4. Existing journeys still work: no successful stubs, inaccessible placeholders,
   destructive migrations, compile failures or failed read → zero.
5. Catalog examples and sandbox scenarios grow with the change; accessibility,
   restoration, financial bounds and error handling are checked where touched.
6. The reviewer checks code, financial/recovery semantics, release isolation and
   evidence, and approves by merging.

### Evidence fields

`evidence.md` carries these fields with no placeholder values (no `TODO`, `TBD`,
`Pending` or `<angle brackets>`); reviewers read them, no tool checks them:

```text
- **Revision:** actual HEAD plus dirty files tested, or exact integrated commit
- **Requirement mapping:** acceptance item → implementation and independent test
- **Red / Green:** exact commands, behavioral failure, passing rerun; docs-only rationale if applicable
- **Gate results:** the pull request whose `quality` check passed, and anything it did not run
- **Device / Artifact:** device/API/config/build and executed journey when one ran locally; otherwise name the hosted check that runs the device suites, or justify inapplicability
- **Review:** self-review, and the pull request whose merge is the reviewer's approval
```

Say why a red test or device gate is inapplicable for a pure documentation ticket;
never waive a required G2/G3 test. Older tickets also carry `Quality run` and
`Acceptance migrated` fields from the retired report binding; they are history.

## Gate profiles

A ticket's `Gate` names what its work must pass: G0 documentation, G1 the host gate,
G2 the host gate plus the device suites its changes affect, G3 the release gate with
the owner's production signing. The pull request's `quality` check runs the gate its
changed paths need; a release ticket also reports the owner's `scripts/check --release`
run ([ADR 0016](../decisions/0016-release-gate-signing-split.md)). What each gate runs,
how paths select device suites and the local commands are in the
[quality gates guide](../development/quality-gates.md). Focused tests run first.

## Increments and history

`Increment` is `MVP` for E01–E08, E12 and E13 and `Post-MVP` for the rest; an MVP
ticket may not depend on a post-MVP one. The MVP shipped as the `0.1.0` candidate
([checklist](../release/mvp-checklist.md)). E09 (signed carryover) and E14
(assistive-technology pass) stay on the post-MVP roadmap; E10 (legacy import) is
done; E15 holds the defects found during release work; E16–E19 bring the v2 screens, Metas,
recurrence and Inversiones to the `0.2.0` release with the decisions in
[ADR 0017](../decisions/0017-goals-recurrence-investments.md).

The retired stabilization epic E11 (SELLO-045–060) is not a ticket set; its reports
and captures were removed and stay in Git history. Retired IDs are never reused.
Until 2026-10-24 the tickets lived in epic files with generated board, roadmap and
Jira CSV views; [ADR 0018](../decisions/0018-ticket-folders.md) records why they
moved here and why nothing generated is committed any more.
