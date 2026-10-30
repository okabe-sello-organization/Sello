# Sello tickets

Planned features live here, one Markdown file per ticket. Bugs and chores that take
less than a day are GitHub issues with a fix pull request instead (issue templates in
`.github/ISSUE_TEMPLATE/`). Scope authority stays with
[ARCHITECTURE.md](../../ARCHITECTURE.md) and [ADR 0001](../decisions/0001-mvp-contract.md);
how work is done is in [AGENTS.md](../../AGENTS.md).

```text
docs/tickets/
  README.md                 this page
  TEMPLATE/                 README.md (epic) and SELLO-0NN.md (ticket) to copy
  E17-goals/
    README.md               the epic: status, increment, goal, exit, Decisions
    SELLO-085.md            one ticket
    attachments/            files other documents link to (rare)
```

## Tickets and epics

- A **ticket** starts with `# SELLO-0NN — Title`, then `- **Type:**` (Task or Story),
  `- **Priority:**` (P0–P2), `- **Status:**`, `- **Depends on:**` (IDs or `None`) and
  `- **Gate:**` (G0–G3, advisory: CI picks the gate from the changed paths). It needs
  `### Outcome` and `### Acceptance criteria`; `### Notes` and `### Progress` are
  optional. Older tickets keep the longer sections they were written with.
- An **epic** README starts with `# SELLO-E0N — Title`, then `- **Status:**`,
  `- **Increment:**` (`MVP` or `Post-MVP`), `- **Goal:**` and `- **Exit:**`. Its
  `## Decisions` section holds the owner's own answers for the epic.
- The file name must equal the ticket ID. Ticket IDs are never reused.

## Statuses

`Backlog → Ready → In Progress → Done`, with `Blocked` and `Review` as detours.

- **Ready:** dependencies Done, acceptance understood, decisions recorded.
- **In Progress:** being built; a ticket may span several pull requests, each saying
  what it delivered under `### Progress`.
- **Blocked:** say the blocker and the next step. **Review:** waiting on something
  other than the merge.
- Ready, In Progress, Review and Done need every dependency Done. An epic is Done only
  when all its tickets are.

## Closing a ticket

The ticket's final pull request sets `Status: Done`, and the epic's too when it is the
last ticket. The pull request description carries what changed and the proof; its
`quality` check proves the checks passed ([quality gates](../development/quality-gates.md)).
Merging approves it. The pull request that closes an epic also refreshes
[HANDOFF](../development/HANDOFF.md). Never change acceptance criteria to fit the
result; propose the change instead.

## Commands

```bash
python3 scripts/quality/tickets.py check        # structure, statuses, dependencies
python3 scripts/quality/tickets.py status E17   # the board for one epic, on demand
python3 scripts/quality/links.py                # every relative link and anchor
```

Nothing generated is committed. Until 2026-10-24 tickets lived in epic files, and until
2026-10-29 in one folder each with evidence files
([ADR 0018](../decisions/0018-ticket-folders.md), [ADR 0021](../decisions/0021-ci-is-the-only-proof.md)).
