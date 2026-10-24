"""Completion evidence: retain a passing quality report and validate it later.

Shared by tickets.py (Review/Done validation) and CI. A retained report is accepted
only if it is well formed, passed, belongs to the ticket, ran at least the local
requirement (gates.local_gate: the host gate G1, or G3 whole for a release ticket;
the device suites of a G2 ticket run hosted on the merge result), is the exact file
the ticket's `Quality run` field names, and still matches the ticket's acceptance
text. A ticket entering Review or Done must also be bound to the
change set under verification: the report's change fingerprint must be one the
candidate contains (fingerprint.candidate_changes). An already-Done ticket keeps its
historical snapshot. The hosted run on the merge result verifies the combination.
"""
import hashlib
import json
import re
import subprocess
from pathlib import Path

import fingerprint
import gates
import selection
import tickets

GATES = ("G0", "G1", "G2", "G3")
MINIMUM_GATE = "G1"
RETAINED_NAME = "quality-report.json"
LEGACY_EPIC_DIR = "docs/planning/mvp/epics/"  # layout before the ticket folders; read at old base revisions
SCHEMA_V1 = json.loads((Path(__file__).with_name("report-schema-v1.json")).read_text(encoding="utf-8"))
SCHEMA = json.loads((Path(__file__).with_name("report-schema-v2.json")).read_text(encoding="utf-8"))
TYPES = {"object": dict, "array": list, "string": str, "integer": int, "boolean": bool}


class EvidenceError(Exception):
    pass


def schema_problem(value, schema, where="report"):
    """First violation of the supported schema keywords, or None."""
    if "const" in schema and value != schema["const"]:
        return f"{where} must be {schema['const']!r}"
    if "enum" in schema and value not in schema["enum"]:
        return f"{where} must be one of {schema['enum']}"
    expected = schema.get("type")
    if expected and (not isinstance(value, TYPES[expected]) or (expected == "integer" and isinstance(value, bool))):
        return f"{where} must be {expected}"
    if "minimum" in schema and value < schema["minimum"]:
        return f"{where} must be at least {schema['minimum']}"
    if "pattern" in schema and not re.search(schema["pattern"], value):
        return f"{where} has an unexpected format"
    for name in schema.get("required", ()):
        if name not in value:
            return f"{where}.{name} is required".removeprefix("report.")
    for name, child in schema.get("properties", {}).items():
        if name in value:
            problem = schema_problem(value[name], child, f"{where}.{name}")
            if problem:
                return problem.removeprefix("report.")
    if "items" in schema:
        for index, item in enumerate(value):
            problem = schema_problem(item, schema["items"], f"{where}[{index}]")
            if problem:
                return problem.removeprefix("report.")
    return None


def retained_path(root, ticket_id):
    """The ticket folder's retained report; raise EvidenceError when the ticket has no folder."""
    folder = tickets.ticket_folder(root, ticket_id)
    if folder is None:
        raise EvidenceError(f"no ticket folder for {ticket_id} under {tickets.TICKETS_DIR}")
    return folder / RETAINED_NAME


def load(path):
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
    except OSError:
        raise EvidenceError("no retained quality report (quality-report.json) in the ticket folder; run scripts/verify-ticket with --retain") from None
    except ValueError:
        raise EvidenceError("retained report is not valid JSON") from None
    schema = SCHEMA_V1 if isinstance(report, dict) and report.get("schema_version") == 1 else SCHEMA
    problem = schema_problem(report, schema)
    if problem:
        raise EvidenceError(f"malformed report: {problem}")
    if report["schema_version"] == 2:
        problem = selection.problem(report["coverage"], report["gate"]["executed"], report["ticket"])
        if problem:
            raise EvidenceError(f"invalid coverage: {problem}")
        if report["outcome"] == "passed" and report["gate"]["executed"] in ("G2", "G3"):
            observed = {}
            for check in report["checks"]:
                for module, counts in check.get("modules", {}).items():
                    if module in observed or not isinstance(counts, dict) or type(counts.get("tests")) is not int or counts["tests"] <= 0:
                        raise EvidenceError("invalid coverage: missing or duplicate device results")
                    if any(counts.get(key) != 0 for key in ("failures", "errors", "skipped")) or check.get("fresh_results") is not True:
                        raise EvidenceError("invalid coverage: device results failed, skipped or lack freshness")
                    observed[module] = counts
            if set(observed) != set(report["coverage"]["device_modules"]):
                raise EvidenceError("invalid coverage: executed modules differ from the selected device coverage")
    return report


def quality_run_line(root, ticket_id):
    """The `Quality run` field text that identifies the retained report."""
    path = retained_path(root, ticket_id)
    report = load(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    scope = report.get("coverage", {"mode": "full", "device_modules": []})
    label = "" if scope["mode"] == "full" else f"; coverage {scope['mode']} ({','.join(scope['device_modules'])})"
    return (
        f"run {report['run_id']}; {report['ticket']} {report['gate']['executed']} {report['outcome']}; "
        f"HEAD {report['snapshot']['head'][:7]}, inputs sha256 {report['snapshot']['fingerprint']['value'][:12]}"
        + (f", changes sha256 {changes['value'][:12]}" if (changes := report["snapshot"].get("changes")) else "")
        + f"; report {path.relative_to(root).as_posix()} sha256 {digest}{label}"
    )


def retain(root, report_path):
    """Copy a passed run's report to the committed location; return its field text."""
    report = load(report_path)
    if report["outcome"] != "passed":
        raise EvidenceError("only a passed run can be retained as completion evidence")
    if not report["ticket"].startswith("SELLO-"):
        raise EvidenceError("report is not tied to a ticket and cannot be completion evidence")
    target = retained_path(root, report["ticket"])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(Path(report_path).read_bytes())
    return quality_run_line(root, report["ticket"])


MIGRATED = re.compile(r"([0-9a-f]{64})\s*(?:→|->)\s*([0-9a-f]{64})")


def check(root, ticket_id, ticket_gate, quality_run, fresh, base=None, migrated=None):
    """Raise EvidenceError unless the retained report completes this ticket.

    [fresh] means the ticket advances in this candidate, so its report must be bound to
    a change set the candidate contains: the working tree against main when [base] is
    None, otherwise one of the first-parent steps from HEAD back to [base].
    [migrated] is the ticket's `Acceptance migrated` evidence field, "<old> → <new>": the
    recorded move of 2026-10-24 changed only link paths inside ticket texts, so a report
    bound to the old hash still completes the ticket while the text hashes to the new one.
    """
    path = retained_path(root, ticket_id)
    report = load(path)
    if report["ticket"] != ticket_id:
        raise EvidenceError(f"report is for {report['ticket']}, not {ticket_id}")
    if report["outcome"] != "passed":
        raise EvidenceError(f"report outcome is {report['outcome']}")
    for record in report["checks"]:
        if record["exit_code"] != 0:
            raise EvidenceError(f"check {record['name']} exited {record['exit_code']} in a report marked passed")
    required = max(ticket_gate, MINIMUM_GATE, key=GATES.index)
    minimum = gates.local_gate(required)
    executed = report["gate"]["executed"]
    if GATES.index(executed) < GATES.index(minimum):
        raise EvidenceError(f"{executed} does not satisfy {minimum}")
    if executed == "G3" and not any(
        record["name"] == "release-production" and record["exit_code"] == 0 for record in report["checks"]
    ):
        raise EvidenceError("a G3 report completes a ticket only with the owner's production signing "
                            "(release-production); CI's throwaway signing cannot")
    if report["run_id"] not in quality_run:
        raise EvidenceError(f"Quality run does not name run {report['run_id']} of the retained report")
    if hashlib.sha256(path.read_bytes()).hexdigest() not in quality_run:
        raise EvidenceError("retained report does not match the recorded sha256; it changed after it was recorded")
    current = fingerprint.ticket_acceptance(root, ticket_id)
    if report["snapshot"]["acceptance"] != current:
        recorded = MIGRATED.search(migrated or "")
        if not (recorded and recorded.group(1) == report["snapshot"]["acceptance"] and recorded.group(2) == current):
            raise EvidenceError("ticket acceptance text changed after the run; verify again")
    if fresh:
        changes = report["snapshot"].get("changes")
        if changes is None:
            raise EvidenceError("report records no change set (schema before change binding); verify again")
        if changes["value"] not in fingerprint.candidate_changes(root, base):
            raise EvidenceError("report is stale: the ticket's own changes differ from the verified ones; verify again")


def status_at(root, base, ticket_id):
    """A ticket's status at a Git revision, or None if unknown there.

    Reads the ticket folder layout first and falls back to the epic files that held
    tickets before it, so a candidate based on an older revision still compares.
    """
    def git(*arguments):
        completed = subprocess.run(["git", "-C", str(root), *arguments], capture_output=True, text=True)
        return completed.stdout if completed.returncode == 0 else None

    status = re.compile(r"^- \*\*Status:\*\* (.+)$", re.MULTILINE)
    names = git("ls-tree", "-r", "--name-only", base, "--", fingerprint.TICKETS_DIR)
    for name in (names or "").splitlines():
        if fingerprint.TICKET_FILE.match(name) and name.split("/")[-2] == ticket_id:
            match = status.search(git("show", f"{base}:{name}") or "")
            return match.group(1) if match else None
    names = git("ls-tree", "-r", "--name-only", base, "--", LEGACY_EPIC_DIR)
    for name in (names or "").splitlines():
        section = fingerprint.ticket_section(git("show", f"{base}:{name}") or "", ticket_id)
        if section:
            match = status.search(section)
            return match.group(1) if match else None
    return None
