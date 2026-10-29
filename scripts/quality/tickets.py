#!/usr/bin/env python3
"""Canonical tickets: one file per ticket, docs/tickets/<epic>/SELLO-0NN.md.

The ticket file holds status, acceptance and dependencies. Proof that checks passed
is the pull request's `quality` check (ADR 0021), so no evidence or report is read
here. Nothing generated is committed: `tickets.py status` prints the board on demand.
"""
import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

TICKETS_DIR = "docs/tickets"
TEMPLATE_DIR = "TEMPLATE"
ATTACHMENTS = "attachments"
STATUSES = ("Backlog", "Ready", "In Progress", "Review", "Blocked", "Done")
REQUIRED_SECTIONS = ("Outcome", "Acceptance criteria")
PRIORITIES = {"P0": "Highest", "P1": "High", "P2": "Medium"}
TICKET_ID = re.compile(r"SELLO-\d{3}")
TICKET_FILE = re.compile(r"SELLO-\d{3}\.md")
EPIC_FOLDER = re.compile(r"E\d{2}-[a-z0-9-]+")


@dataclass
class Ticket:
    identifier: str
    title: str
    epic_id: str
    source: Path
    issue_type: str
    priority: str
    status: str
    dependencies: tuple
    gate: str
    body: str


@dataclass
class Epic:
    identifier: str
    title: str
    source: Path
    status: str
    goal: str
    exit_criteria: str
    tickets: list
    increment: str = "MVP"

    @property
    def folder(self):
        return self.source.parent


def metadata(text):
    entries = re.findall(r"^- \*\*([^*]+):\*\* (.+)$", text, re.MULTILINE)
    result = dict(entries)
    if len(result) != len(entries):
        raise ValueError("Duplicate metadata field")
    return result


def sections(text):
    return dict(re.findall(r"^### ([^\n]+)\n([\s\S]*?)(?=^### |\Z)", text, re.MULTILINE))


def require_fields(fields, required, identifier):
    for name in required:
        if not fields.get(name, "").strip():
            raise ValueError(f"{identifier}: missing {name}")


def parse_epic_readme(source, text):
    heading = re.search(r"^# (SELLO-E\d{2}) — (.+)$", text, re.MULTILINE)
    if not heading:
        raise ValueError(f"{source}: invalid epic heading")
    epic_id, title = heading.groups()
    fields = metadata(text)
    require_fields(fields, ("Status", "Goal", "Exit"), epic_id)
    if int(epic_id.removeprefix("SELLO-E")) >= 9:
        require_fields(fields, ("Increment",), epic_id)
    return Epic(epic_id, title, source, fields["Status"], fields["Goal"], fields["Exit"], [],
                fields.get("Increment", "MVP"))


def parse_ticket(source, text, epic_id):
    heading = re.search(r"^# (SELLO-\d{3}) — (.+)$", text, re.MULTILINE)
    if not heading:
        raise ValueError(f"{source}: invalid ticket heading")
    identifier, title = heading.groups()
    body = text[heading.end():].strip()
    fields = metadata(body.split("###", 1)[0])
    require_fields(fields, ("Type", "Priority", "Status", "Depends on", "Gate"), identifier)
    require_fields(sections(body), REQUIRED_SECTIONS, identifier)
    raw = fields["Depends on"]
    dependencies = () if raw == "None" else tuple(dependency.strip() for dependency in raw.split(","))
    if len(set(dependencies)) != len(dependencies) or any(not TICKET_ID.fullmatch(d) for d in dependencies):
        raise ValueError(f"{identifier}: malformed or duplicate dependencies")
    return Ticket(identifier, title, epic_id, source, fields["Type"], fields["Priority"], fields["Status"],
                  dependencies, fields["Gate"], body)


def load(root):
    """Parse every epic README and ticket file under docs/tickets; raise ValueError on structure errors."""
    base = Path(root) / TICKETS_DIR
    if not base.is_dir():
        raise ValueError(f"No tickets under {TICKETS_DIR}")
    for entry in sorted(base.iterdir()):
        if entry.name.startswith(".") or entry.name in ("README.md", TEMPLATE_DIR):
            continue
        if not (entry.is_dir() and EPIC_FOLDER.fullmatch(entry.name)):
            raise ValueError(f"{entry.relative_to(root)}: not an epic folder (E0N-<slug>)")
    epics = []
    for epic_dir in sorted(path for path in base.iterdir() if path.is_dir() and EPIC_FOLDER.fullmatch(path.name)):
        readme = epic_dir / "README.md"
        if not readme.is_file():
            raise ValueError(f"{epic_dir.relative_to(root)}: missing README.md")
        epic = parse_epic_readme(readme, readme.read_text(encoding="utf-8"))
        folders = sorted(path.name for path in epic_dir.iterdir() if path.is_dir() and TICKET_ID.fullmatch(path.name))
        if folders:
            raise ValueError(f"{epic_dir.relative_to(root)}: ticket folders {', '.join(folders)}; a ticket is one file, SELLO-0NN.md")
        for entry in sorted(epic_dir.iterdir()):
            allowed = (entry.name == "README.md" or (entry.is_dir() and entry.name == ATTACHMENTS)
                       or (entry.is_file() and TICKET_FILE.fullmatch(entry.name)))
            if not (allowed or entry.name.startswith(".")):
                raise ValueError(f"{entry.relative_to(root)}: an epic folder holds only README.md, SELLO-0NN.md files and {ATTACHMENTS}/")
        for source in sorted(path for path in epic_dir.iterdir() if path.is_file() and TICKET_FILE.fullmatch(path.name)):
            ticket = parse_ticket(source, source.read_text(encoding="utf-8"), epic.identifier)
            if ticket.identifier != source.stem:
                raise ValueError(f"{source.relative_to(root)}: file {source.name} holds {ticket.identifier}")
            epic.tickets.append(ticket)
        if not epic.tickets:
            raise ValueError(f"{epic.identifier}: no tickets")
        epics.append(epic)
    if not epics:
        raise ValueError("No epic folders")
    return epics


def validate(epics):
    if not epics:
        raise ValueError("No epic sources")
    epic_ids = [epic.identifier for epic in epics]
    if len(set(epic_ids)) != len(epic_ids):
        raise ValueError("Duplicate epic ID")
    tickets, increments = {}, {}
    for epic in epics:
        if epic.increment not in ("MVP", "Post-MVP"):
            raise ValueError(f"{epic.identifier}: invalid increment")
        if epic.status not in STATUSES:
            raise ValueError(f"{epic.identifier}: unknown status")
        if epic.status == "Done" and any(ticket.status != "Done" for ticket in epic.tickets):
            raise ValueError(f"{epic.identifier}: unfinished children")
        for ticket in epic.tickets:
            if ticket.identifier in tickets:
                raise ValueError(f"Duplicate ticket ID: {ticket.identifier}")
            tickets[ticket.identifier] = ticket
            increments[ticket.identifier] = epic.increment
            if ticket.status not in STATUSES or ticket.priority not in PRIORITIES:
                raise ValueError(f"{ticket.identifier}: invalid status/priority")
            if ticket.issue_type not in ("Task", "Story") or ticket.gate not in ("G0", "G1", "G2", "G3"):
                raise ValueError(f"{ticket.identifier}: invalid type/gate")
    for ticket in tickets.values():
        for dependency in ticket.dependencies:
            if dependency not in tickets:
                raise ValueError(f"{ticket.identifier}: Unknown dependency {dependency}")
            if increments[ticket.identifier] == "MVP" and increments[dependency] == "Post-MVP":
                raise ValueError(f"{ticket.identifier}: MVP depends on Post-MVP {dependency}")
        if ticket.status in ("Ready", "In Progress", "Review", "Done") and any(
            tickets[dependency].status != "Done" for dependency in ticket.dependencies
        ):
            raise ValueError(f"{ticket.identifier}: unfinished dependencies for {ticket.status}")
    visited, active = set(), set()

    def visit(identifier):
        if identifier in active:
            raise ValueError(f"Dependency cycle at {identifier}")
        if identifier in visited:
            return
        active.add(identifier)
        for dependency in tickets[identifier].dependencies:
            visit(dependency)
        active.remove(identifier)
        visited.add(identifier)

    for identifier in tickets:
        visit(identifier)


def status_table(epics, epic_filter=None):
    """A status view printed on demand; it is never committed, so it never conflicts."""
    selected = [epic for epic in epics if not epic_filter or epic_filter in epic.identifier or epic_filter in epic.folder.name]
    lookup = {ticket.identifier: ticket for epic in epics for ticket in epic.tickets}
    tickets = [ticket for epic in selected for ticket in epic.tickets]
    lines = [" · ".join(f"{status}: {sum(t.status == status for t in tickets)}" for status in STATUSES), ""]
    for epic in selected:
        done = sum(ticket.status == "Done" for ticket in epic.tickets)
        lines += [f"## {epic.identifier} — {epic.title} ({epic.status}, {done}/{len(epic.tickets)} done)", "",
                  "| Ticket | Deliverable | Status | Gate | Unmet dependencies |", "| --- | --- | --- | --- | --- |"]
        for ticket in epic.tickets:
            unmet = ", ".join(d for d in ticket.dependencies if lookup.get(d) and lookup[d].status != "Done") or "None"
            lines.append(f"| {ticket.identifier} | {ticket.title} | {ticket.status} | {ticket.gate} | {unmet} |")
        lines.append("")
    return "\n".join(lines)


def main(argv=None, root=None):
    parser = argparse.ArgumentParser(description="Validate the ticket files or print their status.")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check", help="Validate structure, IDs, statuses and dependencies.")
    status = commands.add_parser("status", help="Print the status table on demand.")
    status.add_argument("epic", nargs="?", help="Epic folder or ID filter, for example E17.")
    arguments = parser.parse_args(argv)
    root = Path(root) if root else Path(__file__).resolve().parents[2]
    try:
        epics = load(root)
        if arguments.command == "status":
            print(status_table(epics, arguments.epic))
            return 0
        validate(epics)
    except (ValueError, OSError) as error:
        print(f"Ticket validation failed: {error}", file=sys.stderr)
        return 1
    count = sum(len(epic.tickets) for epic in epics)
    print(f"PASS: {len(epics)} epics, {count} tickets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
