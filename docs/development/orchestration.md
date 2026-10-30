# Orchestrated delivery and adversarial review

How an epic is delivered by several agents, and how the adversarial review works.
[AGENTS.md](../../AGENTS.md) applies to every role; this page adds only what a run
changes.

## Orchestrated run

The owner may start an orchestrated run for an epic. That request authorizes, for that
run only, the delegation, branches, pull requests, comments and merges described here.

- **Orchestrator** (the owner's session): before dispatching anything, asks the owner
  every money, privacy, design or release decision the epic leaves open, and records
  the owner's own answers in the epic README's *Decisions* section (creating it if
  missing). Then it dispatches one planner per ticket in dependency order, dispatches
  the implementer the planner's route names, starts a fresh reviewer for every review
  that [AGENTS.md](../../AGENTS.md#pull-requests-and-merging) requires, posts the
  findings, rules on disputes and reports to the owner. It writes no ticket code and
  owns the epic's status and order. When a check fails because of a defect already on
  `main`, it opens an issue, dispatches a fix pull request and tells the owner. It may
  start a successor ticket early on its predecessor's branch; that pull request waits
  until the predecessor merges.
- **Planner** (Claude Opus 5.5: high effort for a ticket that needs the adversarial
  review below, medium for any other; own worktree; writes nothing until it routes):
  reads the ticket and the code, plans the change and routes it. It forwards the ticket
  only when all of these hold; otherwise it implements the ticket itself:
  - the ticket needs no adversarial review;
  - the plan leaves no open choice: files, State and Action shapes, test cases with
    hand-worked money values, and the components to reuse;
  - the plan is clearly shorter than the code it describes.

  To forward, it writes the plan to `~/.cache/sello-plans/SELLO-0NN.md`, never inside
  a repository, and reports the route and that path to the orchestrator in one line.
- **Implementer** (the planner itself, or Claude Sonnet 5.5 at high effort for a
  forwarded ticket; own worktree; branch from `origin/main` unless stacked): delivers
  one ticket, opens the pull request, tells the orchestrator, then looks after it:
  answers every finding and comment with a fix or a reason, fixes red checks, and
  merges as below. It owns its branch and ticket file only, starts no subagents and
  never counts its own review as approval.
  - A forwarded implementer gets the plan's path in its brief, never its text. When
    the plan doesn't match the code, it stops and reports instead of improvising.
  - Before opening the pull request it records the plan's decisions and the route
    (planned by, implemented by) in the description, then deletes the plan file. The
    orchestrator checks that the file is gone when the pull request is reported.
  - The orchestrator hands a forwarded ticket to a Claude Opus 5.5 implementer at
    medium effort (high for a plan that turned out to touch adversarial-review areas)
    when its implementer reports a mismatch, or when a review asks twice for changes
    beyond a small fix. Before the pull request opens it passes the plan's path;
    after, the pull request and its findings.
- **Reviewer** (Claude Opus 5.5, read-only): high effort for the adversarial review
  below, medium for the lighter review [AGENTS.md](../../AGENTS.md#pull-requests-and-merging)
  asks of other code.

Every agent is started with its model and effort set explicitly. The owner may set
other models for a run in the epic's *Decisions*; those win over this page.

### Merging in a run

The owner delegates the merge for the run, including changes that the owner would
otherwise merge, except a release. The implementer squash-merges with
`gh pr merge <n> --squash` only when all of these hold on the current head:

- the latest verdict is `APPROVE`, or every open finding is Minor or carries an
  orchestrator ruling in a comment;
- every comment and review thread is answered, and none from the owner is open;
- every check is green, and none was skipped by changing a gate, test or workflow;
- `main` is green (push run and nightly);
- any owner approval the ticket needs is recorded in the epic's *Decisions* from the
  owner's own answers (a reviewer's verdict never counts as one);
- pull requests stacked on its branch were retargeted to `main` first
  (`gh pr edit <n> --base main`): merging a base with `--delete-branch` closes them.

Never `--admin`, bypass a ruleset or merge a release without the owner's go for that
release. If any condition fails, the implementer reports and waits.

## Adversarial review

Its only inputs are the ticket file, the pull request diff against its base, the
changed files at the head, and the rules it judges by: AGENTS.md, ARCHITECTURE.md and
the decisions the ticket names. It never sees the pull request description, the
author's report or chat history, so it judges what the change does, not what its
author says. It edits nothing, runs nothing and posts nothing.

Assume the change is wrong until the diff proves otherwise. Check, citing file:line:

- each acceptance criterion: met, partly met or missing;
- money and recovery: exact types, rounding, typed failures, operation IDs, versions,
  cancellation; no silent repair, clipping, or failed read turned into zero;
- tests: a bug fix's test fails for the reported reason; expected money values are
  hand-worked literals; the AGENTS.md checklist is covered or its gaps justified;
  nothing was weakened, disabled or skipped;
- boundaries, schema, backup format, privacy and release isolation;
- scope: unrelated edits, dormant features, speculative abstractions;
- claims: status, approvals or test runs the diff doesn't support.

Report a verdict, `APPROVE` or `CHANGES REQUIRED`, then findings ranked Blocking,
Important or Minor. Each has an ID (R1, R2…), file:line, the rule or criterion, a
concrete failure scenario and the smallest fix. List what can't be verified from the
diff separately. No praise and no taste: style counts only where a rule names it.

A re-review is a new reviewer given the earlier findings, the author's replies and the
fix diff; it marks each finding resolved or open and flags new breakage in the fix
diff only. When the latest verdict is `APPROVE` and only Minor findings were fixed,
the orchestrator (or the author outside a run) may check that fix diff itself and say
so in a comment instead. After three rounds, the orchestrator rules on each open
finding in a comment, or asks the owner when the dispute is about money, privacy or
data-deleting behaviour. Comments name the reviewer as an agent; its verdict is never
recorded as the owner's approval.

Outside a run, whoever opens a pull request that needs this review starts the
reviewer, posts the verdict as one comment and answers each finding the same way.
