# 0018 — One folder per ticket replaces the board

**Owner:** project owner (user) · **Date:** 2026-10-24
**Status:** user-authorized implementation.

## Decision

Tickets live in `docs/tickets/<epic>/<SELLO-0NN>/`: `ticket.md` (canonical, the only
file the tools parse), `evidence.md`, `quality-report.json`, `handoff.md` and
`artifacts/`. The epic's `README.md` holds its status, goal, exit and owner decisions
and no per-ticket lines. The generated board, roadmap and Jira CSV exports, the
per-epic Markdown files, the shared `quality-reports/` folder, `docs/testing/evidence/`
and the per-ticket rows of `HANDOFF.md` are gone. `scripts/quality/tickets.py`
replaces `board.py`: it validates the folders and prints a status table on demand.
`scripts/quality/links.py` checks every relative Markdown link and anchor and joins G0.

## Why

With several pull requests open, every ticket PR edited the same files: the epic
file, `HANDOFF.md`, `ROADMAP.md` and the CSVs. Two of four simulated pairs of recent
PRs conflicted in `HANDOFF.md` and `ROADMAP.md`. A ticket's record was also spread
over five places. The board and the Jira exports were pre-MVP conveniences nobody
imported; the owner asked to remove them and to keep each ticket's evidence and
handoff in one place. After this decision a ticket PR touches its own folder and its
own code, and nothing shared, so two PRs conflict only when they truly edit the same
lines of the same work.

## Evidence continuity

The move keeps every ticket's acceptance text byte-identical except for 17 Done
tickets whose links pointed at moved documents. For those, the move recorded
`- **Acceptance migrated:** <old> → <new>` in `evidence.md`, after proving that the
retained report was bound to the old hash and that only link targets differ. The
validator accepts exactly that pair; any later acceptance edit still fails. The
retired SELLO-045–060 reports and captures, and the one-time script
`scripts/one-off/restructure_tickets.py`, were later removed; Git history keeps them.

## Consequences

No file is regenerated on every ticket; nothing generated is committed. A Jira
import would need a new exporter; none is planned. Historical quality reports still
name `docs/planning/mvp/quality-reports/...` in their recorded command lines; that is
history, not a path to follow. `HANDOFF.md` is refreshed when an epic closes, not per
ticket. The link check makes a moved or deleted document fail the gate instead of a
reader's path. Amends [ADR 0006](0006-quality-tooling.md) (evidence location) and the
guardrails in AGENTS.md and the tickets README.
