"""Ticket quality runner: derive the gate from the canonical ticket, run it, write a report.

There is deliberately no option to skip a check or accept a lower gate. A refusal
(unknown ticket, unfinished prerequisite, missing toolchain/device, unavailable gate)
exits nonzero before anything runs; a failed or incomplete check exits nonzero with
a report whose outcome is "failed".
"""
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

import evidence
import fingerprint
import gates
import selection
import tickets
import hashlib

REPOSITORY = Path(__file__).resolve().parents[2]

SCHEMA_VERSION = 2
UNTICKETED = "UNTICKETED"  # a merge candidate that advances no ticket
REPORT_DIR = "build/reports/quality"
TICKETS = tickets.TICKETS_DIR


class Refusal(Exception):
    """The run cannot start; nothing was executed and no report exists."""


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_tickets(root):
    return {ticket.identifier: ticket for epic in tickets.load(root) for ticket in epic.tickets}


def resolve_gate(tickets, ticket_id, requested_gate):
    rank = gates.GATES.index
    if requested_gate is not None and requested_gate not in gates.GATES:
        raise Refusal(f"Unknown gate {requested_gate}")
    if ticket_id == UNTICKETED:
        required = gates.MINIMUM_GATE
        return required, max(requested_gate or required, required, key=rank)
    if ticket_id not in tickets:
        raise Refusal(f"Unknown ticket {ticket_id}; canonical tickets live in {TICKETS}<epic>/<ticket>/ticket.md")
    ticket = tickets[ticket_id]
    for dependency in ticket.dependencies:
        status = tickets[dependency].status if dependency in tickets else "missing"
        if status != "Done":
            raise Refusal(f"{ticket_id} cannot be verified: prerequisite {dependency} is {status}, not Done")
    required = max(ticket.gate, gates.MINIMUM_GATE, key=rank)
    if requested_gate is None:
        return required, required
    if rank(requested_gate) < rank(required):
        raise Refusal(f"{ticket_id} requires {required}; {requested_gate} would be a downgrade")
    return required, requested_gate


def default_execute(argv, root, log_path):
    with open(log_path, "wb") as log:
        return subprocess.run(argv, cwd=root, stdout=log, stderr=subprocess.STDOUT).returncode


def first_line(argv):
    try:
        completed = subprocess.run(argv, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        return None
    output = (completed.stdout + completed.stderr).strip().splitlines()
    return output[0] if completed.returncode == 0 and output else None


def sdk_directory(root):
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(variable):
            return Path(os.environ[variable])
    properties = Path(root) / "local.properties"
    if properties.is_file():
        match = re.search(r"^sdk\.dir=(.+)$", properties.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            return Path(match.group(1).strip())
    return None


def documentation_environment(root):
    return {"os": f"{platform.system()} {platform.machine()}", "python": platform.python_version(),
            "git": first_line(["git", "--version"])}


def default_environment(root):
    """Tool versions only: no host name, user name or machine paths."""
    java = first_line(["java", "-version"])
    if java is None:
        raise Refusal("No working `java` on PATH; set JAVA_HOME/PATH as in docs/development/setup.md")
    sdk = sdk_directory(root)
    if sdk is None or not sdk.is_dir():
        raise Refusal("Android SDK not found; set ANDROID_HOME or sdk.dir as in docs/development/setup.md")
    wrapper = (Path(root) / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    catalog = (Path(root) / "gradle/libs.versions.toml").read_text(encoding="utf-8")

    def pin(pattern, text):
        match = re.search(pattern, text, re.MULTILINE)
        return match.group(1) if match else None

    return {
        "os": f"{platform.system()} {platform.machine()}",
        "python": platform.python_version(),
        "java": java,
        "git": first_line(["git", "--version"]),
        "gradle": pin(r"gradle-([\d.]+)-", wrapper),
        "agp": pin(r'^agp = "([^"]+)"', catalog),
        "kotlin": pin(r'^kotlin = "([^"]+)"', catalog),
    }


DEVICE_QUERY_SECONDS = 20


def default_devices():
    adb = shutil.which("adb")
    sdk = sdk_directory(REPOSITORY)
    if adb is None and sdk is not None and (sdk / "platform-tools/adb").is_file():
        adb = str(sdk / "platform-tools/adb")
    if adb is None:
        return []
    listing = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout
    serials = [line.split()[0] for line in listing.splitlines()[1:] if line.split()[1:2] == ["device"]]
    # With ANDROID_SERIAL set, no other device is asked anything: one busy elsewhere,
    # such as a phone running another checkout's tests, must not hold this gate.
    selected = os.environ.get("ANDROID_SERIAL")
    if selected:
        serials = [serial for serial in serials if serial == selected]

    def prop(serial, name):
        # A device that does not answer is reported without the value, and the gate
        # then refuses it, instead of waiting for it forever.
        try:
            return subprocess.run(
                [adb, "-s", serial, "shell", "getprop", name],
                capture_output=True,
                text=True,
                timeout=DEVICE_QUERY_SECONDS,
            ).stdout.strip()
        except subprocess.TimeoutExpired:
            return ""

    return [
        {"serial": serial, "api": prop(serial, "ro.build.version.sdk"), "model": prop(serial, "ro.product.model")}
        for serial in serials
    ]


def snapshot(root, base=None):
    """What the run verified: HEAD, dirty files, the whole-tree inputs and the change set.

    The change set is compared with [base] when given (CI knows its candidate base),
    otherwise with the merge base of HEAD and main.
    """
    def git(*arguments):
        return subprocess.run(
            ["git", "-C", str(root), *arguments], check=True, capture_output=True, text=True
        ).stdout

    entries = git("status", "--porcelain", "-z", "--untracked-files=all").split("\0")
    dirty, skip = [], False
    for entry in filter(None, entries):
        if skip:
            skip = False
            continue
        dirty.append(entry[3:])
        skip = entry[0] in "RC"
    return {
        "head": git("rev-parse", "HEAD").strip(),
        "dirty": bool(dirty),
        "dirty_files": sorted(dirty),
        "fingerprint": fingerprint.input_fingerprint(root),
        "changes": fingerprint.change_fingerprint(root, base or fingerprint.change_base(root)),
    }


def run_check(check, root, directory, execute):
    marker = directory / f".{check.name}.started"
    marker.touch()
    started_mtime = marker.stat().st_mtime_ns
    record = {"name": check.name, "command": " ".join(check.argv), "log": f"{check.name}.log", "started_at": now()}
    record["exit_code"] = execute(list(check.argv), root, directory / record["log"])
    record["finished_at"] = now()
    marker.unlink()
    failures = []
    if record["exit_code"] != 0:
        failures.append(f"{check.name} exited {record['exit_code']}")
    if check.results:
        newer_than = started_mtime if check.fresh_results else None
        if check.result_modules:
            modules = gates.device_results(root, check.result_modules, newer_than)
            record["modules"] = modules
            for module, counts in modules.items():
                if counts["tests"] == 0:
                    failures.append(f"{check.name}: {module} produced no fresh test results")
                if counts["skipped"]:
                    failures.append(f"{check.name}: {module} skipped device tests")
            results = gates.sum_results(modules.values())
        else:
            results = gates.test_results(root, check.results, newer_than)
        record["results"] = results
        record["fresh_results"] = check.fresh_results
        if results["tests"] == 0:
            failures.append(f"{check.name} produced no test results")
        if results["failures"] + results["errors"]:
            failures.append(f"{check.name} reported {results['failures'] + results['errors']} failing tests")
    if check.lint:
        record["lint"] = gates.lint_results(root)
        if record["lint"]["errors"]:
            failures.append(f"{check.name} reported {record['lint']['errors']} lint errors")
    return record, failures


def run(ticket_id, root, requested_gate=None, execute=default_execute,
        environment=default_environment, devices=default_devices, stream=sys.stderr, retain=False,
        affected_base=None, coverage=None):
    """Return (exit code, report path or None)."""
    root = Path(root)
    try:
        if ticket_id == UNTICKETED and requested_gate == "G0" and coverage is not None:
            required, executed = "G0", "G0"
        else:
            required, executed = resolve_gate(load_tickets(root), ticket_id, requested_gate)
        if coverage is None:
            base = resolve_base(root, affected_base or "HEAD")
            coverage = selection.coverage(base, changed_paths(root, base) if affected_base else (),
                                          gate=executed, full=not affected_base or requested_gate is not None)
        problem = selection.problem(coverage, executed, ticket_id)
        if problem:
            raise Refusal(problem)
        checks = gates.plan(executed)
        if executed == "G2":
            checks[-1] = gates.device_check(tasks=tuple(task for task in gates.DEVICE_TASKS
                                                       if task.split(":")[1] in coverage["device_modules"]))
        tools = documentation_environment(root) if executed == "G0" else environment(root)
        attached = devices() if any(check.needs_device for check in checks) else []
        if any(check.needs_device for check in checks):
            serial = os.environ.get("ANDROID_SERIAL")
            if serial:
                attached = [device for device in attached if device["serial"] == serial]
            if not attached:
                raise Refusal(
                    f"{executed} needs device tests but there is no connected device; start the pinned "
                    "emulator from docs/development/setup.md. Assembling test APKs is not a substitute"
                )
            if len(attached) != 1 or attached[0]["api"] != "30":
                raise Refusal("G2 needs exactly one selected API-30 device; set ANDROID_SERIAL")
    except (Refusal, gates.Unavailable, ValueError, OSError) as error:
        print(f"REFUSED: {error}", file=stream)
        return 2, None

    print(f"Coverage: {coverage['mode']}; device modules: {', '.join(coverage['device_modules']) or 'none'}", file=stream)
    started_at = now()
    run_id = f"{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}-{uuid.uuid4().hex[:8]}"
    directory = root / REPORT_DIR / ticket_id / run_id
    directory.mkdir(parents=True)
    before = snapshot(root)
    before["acceptance"] = fingerprint.ticket_acceptance(root, ticket_id) or hashlib.sha256(b"").hexdigest()
    records, failures, warnings = [], [], []
    for check in checks:
        print(f"[{ticket_id} {executed}] {check.name} ...", file=stream, flush=True)
        record, problems = run_check(check, root, directory, execute)
        records.append(record)
        failures.extend(problems)
        if record.get("lint", {}).get("warnings"):
            warnings.append(f"{check.name}: {record['lint']['warnings']} lint warnings")
        print(f"[{ticket_id} {executed}] {check.name}: {'FAILED' if problems else 'ok'}", file=stream, flush=True)
    return finish_run(
        root, ticket_id, required, executed, before, tools, attached, records,
        failures, warnings, started_at, run_id, directory, stream, retain, coverage=coverage,
    )


def finish_run(root, ticket_id, required, executed, before, tools, attached, records,
               failures, warnings, started_at, run_id, directory, stream=sys.stderr, retain=False,
               execution=None, coverage=None):
    """Write an honest full-gate report from executed checks, local or collected."""
    if snapshot(root)["fingerprint"] != before["fingerprint"]:
        failures.append("inputs changed while checks were running")
    if before["dirty"]:
        warnings.append("tested an uncommitted working tree, not a clean commit")
    report = {
        "schema_version": SCHEMA_VERSION,
        "coverage": coverage or selection.coverage(before["head"], gate=executed, full=True),
        "run_id": run_id,
        "ticket": ticket_id,
        "gate": {"required": required, "executed": executed},
        "outcome": "failed" if failures else "passed",
        "failures": failures,
        "warnings": warnings,
        "snapshot": before,
        "environment": tools,
        "devices": attached,
        "checks": records,
        "started_at": started_at,
        "finished_at": now(),
    }
    if execution is not None:
        report["execution"] = execution
    path = directory / "report.json"
    path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for failure in failures:
        print(f"FAILED: {failure}", file=stream)
    label = f"{executed} {report['coverage']['mode']} ({', '.join(report['coverage']['device_modules']) or 'no device suites'})"
    print(f"{report['outcome'].upper()}: {ticket_id} {label}, run {run_id}", file=stream)
    print(f"Report: {path.relative_to(root)}", file=stream)
    if retain and not failures:
        line = evidence.retain(root, path)
        print(f"Retained: {evidence.retained_path(root, ticket_id).relative_to(root).as_posix()}", file=stream)
        print(f"- **Quality run:** {line}", file=stream)
    elif retain:
        print("Not retained: only a passed run is completion evidence", file=stream)
    return (1 if failures else 0), path



def merge_candidate_tickets(root, base):
    """Tickets this candidate advances: active ones and those completed since the base revision."""
    return sorted(
        identifier for identifier, ticket in load_tickets(root).items()
        if ticket.status in ("In Progress", "Review")
        or (ticket.status == "Done" and evidence.status_at(root, base, identifier) != "Done")
    )


def run_merge_candidate(base, root, requested_gate=None, stream=sys.stderr, **options):
    """Verify every ticket a merge candidate advances, or the bare gate if it advances none.

    Return (exit code, report paths).
    """
    root = Path(root)
    known = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}"], capture_output=True
    ).returncode == 0
    if not known:
        print(f"REFUSED: Unknown base revision {base}; fetch full history before verifying", file=stream)
        return 2, []
    try:
        selected = merge_candidate_tickets(root, base) or [UNTICKETED]
    except (ValueError, OSError) as error:
        print(f"REFUSED: {error}", file=stream)
        return 2, []
    print(f"Merge candidate against {base[:12]}: {', '.join(selected)}", file=stream)
    try:
        candidate = candidate_plan(root, base)
        tickets = load_tickets(root)
        for identifier in selected:
            if identifier != UNTICKETED:
                resolve_gate(tickets, identifier, requested_gate)
    except (Refusal, ValueError, OSError) as error:
        print(f"REFUSED: {error}", file=stream)
        return 2, []
    worst, paths = 0, []
    for identifier in selected:
        chosen = requested_gate or (candidate["gate"] if identifier == UNTICKETED else None)
        scope = None
        if requested_gate is None:
            executed = chosen or resolve_gate(load_tickets(root), identifier, None)[1]
            scope = selection.coverage(candidate["coverage"]["base"], candidate["coverage"]["changed_paths"], executed)
        code, path = run(identifier, root, requested_gate=chosen, stream=stream, coverage=scope, **options)
        worst = max(worst, code)
        if path is not None:
            paths.append(path)
    return worst, paths


def is_document(path):
    return selection.is_document(path)


def resolve_base(root, base):
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "--verify", f"{base}^{{commit}}"],
                            capture_output=True, text=True)
    if result.returncode:
        raise Refusal(f"Unknown base revision {base}")
    return result.stdout.strip()


def candidate_plan(root, base):
    base = resolve_base(root, base)
    paths = changed_paths(root, base)
    gate = "G2" if merge_candidate_tickets(root, base) or any(not is_document(p) for p in paths) else "G0"
    return {"gate": gate, "coverage": selection.coverage(base, paths, gate)}


def changed_paths(root, base):
    """Every path that differs from [base], committed or not, with both ends of a move."""
    def git(*arguments):
        return subprocess.run(
            ["git", "-C", str(root), *arguments], check=True, capture_output=True, text=True
        ).stdout

    committed = git("diff", "--name-only", "--no-renames", "-z", base, "--")
    untracked = git("ls-files", "--others", "--exclude-standard", "-z")
    return sorted(set(filter(None, (committed + untracked).split("\0"))))


def candidate_gate(root, base):
    """Uncertain comparisons require full device verification."""
    try:
        return candidate_plan(root, base)["gate"]
    except (Refusal, ValueError, OSError, subprocess.CalledProcessError):
        return "G2"


def run_landed(base, root, stream=sys.stderr, **options):
    """Check what has just merged into main. Return (exit code, report paths).

    The candidate was verified at its own gate, on its merge commit, before it could
    merge, and main only takes candidates that are up to date. So this runs the host
    gate whatever tickets the merge completed; their evidence is validated separately.
    """
    root = Path(root)
    known = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}"], capture_output=True
    ).returncode == 0
    if not known:
        print(f"REFUSED: Unknown base revision {base}; fetch full history before verifying", file=stream)
        return 2, []
    candidate = candidate_plan(root, base)
    gate = "G0" if candidate["gate"] == "G0" else "G1"
    print(f"Landed on main since {base[:12]}: {gate}", file=stream)
    scope = selection.coverage(resolve_base(root, base), changed_paths(root, base), gate=gate)
    code, path = run(UNTICKETED, root, requested_gate=gate, coverage=scope, stream=stream, **options)
    return code, [path] if path is not None else []
