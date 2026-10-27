"""Fixed CI execution parts. Only collection of all required parts can prove a gate."""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ElementTree
from datetime import datetime, timezone
from pathlib import Path

import fingerprint
import gates
import runner
import evidence
import selection

PARTS = ("host", *gates.DEVICE_GROUPS)
EVENTS = ("pull_request", "push", "schedule", "workflow_dispatch")
PART_DIRECTORY = "build/reports/quality-parts"
PART_SCHEMA = {
    "type": "object",
    "required": ["kind", "version", "part", "context", "snapshot", "environment", "devices",
                 "checks", "outcome", "failures", "warnings", "started_at", "finished_at"],
    "properties": {
        "kind": {"const": "sello-quality-part"}, "version": {"const": 1},
        "part": {"enum": list(PARTS)}, "context": {"type": "object"},
        "snapshot": {
            "type": "object", "required": ["head", "dirty", "dirty_files", "fingerprint"],
            "properties": evidence.SCHEMA["properties"]["snapshot"]["properties"],
        },
        "environment": {
            "type": "object",
            "properties": {key: {"type": "string", "pattern": r"\S"} for key in ("java", "gradle", "agp", "kotlin")},
        },
        **{key: evidence.SCHEMA["properties"][key] for key in
           ("devices", "checks", "outcome", "failures", "warnings", "started_at", "finished_at")},
    },
}


def session():
    run = os.environ.get("GITHUB_RUN_ID")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT")
    if run and attempt and re.fullmatch(r"[1-9][0-9]*", attempt):
        return {"run": run, "attempt": attempt}
    local = os.environ.get("QUALITY_EXECUTION_ID")
    if local and os.environ.get("GITHUB_ACTIONS") != "true":
        return {"run": local, "attempt": "local"}
    raise runner.Refusal("CI parts need a workflow run/attempt, or a local QUALITY_EXECUTION_ID")


def artifact_name(part, plan):
    return f"quality-part-{plan['session']['attempt']}-{part}"


def context(root, base, event):
    if event not in EVENTS:
        raise runner.Refusal(f"Unknown CI event {event!r}")
    resolved = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", f"{base}^{{commit}}"],
        capture_output=True, text=True,
    )
    if resolved.returncode:
        raise runner.Refusal(f"Unknown base revision {base}")
    gates.validate_device_groups()
    candidate = runner.candidate_plan(root, base)
    if event == "push":
        gate = "G0" if candidate["gate"] == "G0" else "G1"
    elif event == "pull_request":
        gate = candidate["gate"]
    else:
        # A nightly or manual run verifies the whole of main: every device suite and the
        # release checks, with CI's throwaway key (ADR 0009, amendment of 2026-10-26).
        gate = "G3"
    scope = selection.coverage(resolved.stdout.strip(), candidate["coverage"]["changed_paths"], gate,
                               full=event in ("schedule", "workflow_dispatch"))
    selected = [runner.UNTICKETED] if event == "push" else runner.merge_candidate_tickets(root, base) or [runner.UNTICKETED]
    tickets = runner.load_tickets(root)
    required = {}
    for identifier in selected:
        if gate == "G0" and identifier == runner.UNTICKETED:
            required[identifier] = "G0"
        else:
            required[identifier] = runner.resolve_gate(tickets, identifier, None)[0]
    if event != "push" and "G3" in required.values():
        # A release ticket is verified at G3; CI's part of it signs with a throwaway key.
        gate = "G3"
        scope = selection.coverage(resolved.stdout.strip(), candidate["coverage"]["changed_paths"], gate)
    for identifier, minimum in required.items():
        # Hosted execution meets each ticket's own gate; the local default (G1) does not apply here.
        if minimum != "G0" and gates.GATES.index(gate) < gates.GATES.index(minimum):
            raise runner.Refusal(f"{identifier} requires {minimum}; hosted {gate} would be a downgrade")
    gates.plan(gate)  # unavailable gates cannot be executed in parts either
    return {"base": resolved.stdout.strip(), "event": event, "gate": gate, "tickets": required,
            "session": session(), "coverage": scope, "groups": selection.groups(scope)}


def checks_for(part, plan=None):
    if part == "host":
        if plan and plan["gate"] == "G3":
            return gates.plan("G1") + gates.release_checks(ci=True)
        return gates.plan("G0" if plan and plan["gate"] == "G0" else "G1")
    if part not in gates.DEVICE_GROUPS:
        raise runner.Refusal(f"Unknown CI part {part}")
    tasks = gates.DEVICE_GROUPS[part]
    if plan:
        tasks = tuple(task for task in tasks if task.split(":")[1] in plan["coverage"]["device_modules"])
    return [gates.device_check(f"gradle-device-{part}", tasks)]


def raw_patterns(checks):
    patterns = []
    for check in checks:
        if check.result_modules:
            patterns.extend(f"{module}/build/outputs/androidTest-results/**/*" for module in check.result_modules)
        elif check.results:
            patterns.extend((check.results, "*/build/test-results/**/*"))
        if check.lint:
            patterns.extend(("*/build/reports/lint-results-debug.*", "*/build/reports/ktlint/**/*"))
    return patterns


def capture_raw(root, directory, checks):
    for pattern in raw_patterns(checks):
        for path in sorted(root.glob(pattern)):
            if path.is_file():
                relative = path.relative_to(root)
                target = directory / "raw" / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, target)


def run_part(root, base, event, part, output=PART_DIRECTORY, execute=runner.default_execute,
             environment=runner.default_environment, devices=runner.default_devices, stream=sys.stderr):
    root = Path(root)
    try:
        plan = context(root, base, event)
        if part not in PARTS or (part != "host" and part not in plan["groups"]):
            raise runner.Refusal(f"CI part {part} is not required for {plan['gate']}")
        checks = checks_for(part, plan)
        tools = runner.documentation_environment(root) if plan["gate"] == "G0" else environment(root)
        attached = devices() if part != "host" else []
        if part != "host":
            serial = os.environ.get("ANDROID_SERIAL")
            if serial:
                attached = [device for device in attached if device["serial"] == serial]
            if len(attached) != 1 or attached[0]["api"] != "30":
                raise runner.Refusal("a device part needs exactly one selected API-30 device; set ANDROID_SERIAL")
        directory = root / output / artifact_name(part, plan)
        if directory.exists():
            raise runner.Refusal(f"part output already exists: {directory}; use a fresh --ci-output directory")
    except (runner.Refusal, gates.Unavailable, ValueError, OSError) as error:
        print(f"REFUSED: {error}", file=stream)
        return 2, None

    directory.mkdir(parents=True)
    started = runner.now()
    before = runner.snapshot(root, plan["base"])
    records, failures, warnings = [], [], []
    for check in checks:
        print(f"[CI part {part}] {check.name} ...", file=stream, flush=True)
        try:
            record, problems = runner.run_check(check, root, directory, execute)
        except (OSError, ValueError, ElementTree.ParseError) as error:
            failures.append(f"{check.name}: could not validate results: {error}")
            continue
        records.append(record)
        failures.extend(problems)
        if record.get("lint", {}).get("warnings"):
            warnings.append(f"{check.name}: {record['lint']['warnings']} lint warnings")
    if runner.snapshot(root)["fingerprint"] != before["fingerprint"]:
        failures.append("inputs changed while checks were running")
    capture_raw(root, directory, checks)
    report = {
        "kind": "sello-quality-part", "version": 1, "part": part, "context": plan,
        "snapshot": before, "environment": tools, "devices": attached, "checks": records,
        "outcome": "failed" if failures else "passed", "failures": failures, "warnings": warnings,
        "started_at": started, "finished_at": runner.now(),
    }
    path = directory / "part.json"
    path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{report['outcome'].upper()}: CI part {part}; not full-gate completion evidence", file=stream)
    for problem in failures:
        print(f"FAILED: {problem}", file=stream)
    print(f"Part report: {path.relative_to(root) if path.is_relative_to(root) else path}", file=stream)
    return (1 if failures else 0), path


def job_failures(gate, results):
    expected = {"plan": "success", "host": "success", "device": "success" if gate in ("G2", "G3") else "skipped"}
    return [f"{job} job was {results.get(job, 'missing')}, expected {state}"
            for job, state in expected.items() if results.get(job) != state]


def load_part(directory, part, plan, before):
    path = directory / "part.json"
    report = json.loads(path.read_text(encoding="utf-8"))
    problem = evidence.schema_problem(report, PART_SCHEMA)
    if problem:
        raise ValueError(f"invalid partial report: {problem}")
    if report.get("part") != part or report.get("context") != plan:
        raise ValueError("part, event, base, gate, ticket or workflow run/attempt mismatch")
    snapshot = report["snapshot"]
    if snapshot["head"] != before["head"] or snapshot["fingerprint"] != before["fingerprint"]:
        raise ValueError("stale commit or input fingerprint")
    if report["outcome"] != "passed" or report["failures"]:
        raise ValueError("part did not pass: " + "; ".join(report["failures"]))
    expected = checks_for(part, plan)
    if plan["gate"] != "G0" and any(not report["environment"].get(key) for key in ("java", "gradle", "agp", "kotlin")):
        raise ValueError("missing toolchain evidence")
    records = report["checks"]
    if [record["name"] for record in records] != [check.name for check in expected]:
        raise ValueError("missing, duplicate or unexpected checks")
    if part != "host" and (len(report["devices"]) != 1 or report["devices"][0]["api"] != "30"):
        raise ValueError("missing API-30 device evidence")
    for check, record in zip(expected, records):
        if record["command"] != " ".join(check.argv) or type(record["exit_code"]) is not int or record["exit_code"] != 0:
            raise ValueError(f"{check.name}: failed or changed command")
        if record["log"] != f"{check.name}.log" or not (directory / record["log"]).is_file():
            raise ValueError(f"{check.name}: missing or unexpected log")
        if check.results:
            if type(record.get("fresh_results")) is not bool or record["fresh_results"] != check.fresh_results:
                raise ValueError(f"{check.name}: missing result freshness validation")
            if check.result_modules:
                modules = gates.device_results(directory / "raw", check.result_modules)
                if modules != record.get("modules") or any(counts["tests"] <= 0 or counts["skipped"] for counts in modules.values()):
                    raise ValueError(f"{check.name}: missing or incomplete module results")
                results = gates.sum_results(modules.values())
            else:
                results = gates.test_results(directory / "raw", check.results)
            if results != record.get("results") or results["tests"] <= 0 or results["failures"] or results["errors"]:
                raise ValueError(f"{check.name}: missing, failed or inconsistent test results")
        if check.lint:
            lint = gates.lint_results(directory / "raw")
            if lint != record.get("lint") or lint["reports"] == 0 or lint["errors"]:
                raise ValueError(f"{check.name}: missing or failed lint results")
    return report


def collect(root, base, event, inputs, stream=sys.stderr, job_results=None):
    root, inputs = Path(root), Path(inputs)
    if not inputs.is_absolute():
        inputs = root / inputs
    try:
        plan = context(root, base, event)
    except (runner.Refusal, gates.Unavailable, ValueError, OSError) as error:
        print(f"REFUSED: {error}", file=stream)
        return 2, []
    before = runner.snapshot(root, plan["base"])
    failures, warnings, records, attached, parts = [], [], [], [], []
    if job_results is None and os.environ.get("GITHUB_ACTIONS") == "true":
        job_results = {name: os.environ.get(f"QUALITY_{name.upper()}_RESULT", "missing") for name in ("plan", "host", "device")}
    if job_results is not None:
        failures.extend(job_failures(plan["gate"], job_results))
    expected_parts = ("host", *plan["groups"])
    actual_parts = {p.name for p in inputs.glob("quality-part-*")}
    # download-artifact v8 extracts a single matched artifact directly into path,
    # even with merge-multiple:false. G0/G1 consist of one host artifact.
    flat_host = not plan["groups"] and not actual_parts and (inputs / "part.json").is_file()
    if not flat_host and actual_parts != {artifact_name(part, plan) for part in expected_parts}:
        failures.append("missing, duplicate or unexpected part artifacts")
    for part in expected_parts:
        directory = inputs if flat_host else inputs / artifact_name(part, plan)
        try:
            report = load_part(directory, part, plan, before)
        except (OSError, ValueError, KeyError, TypeError, ElementTree.ParseError) as error:
            failures.append(f"{part}: {error}")
            continue
        parts.append((part, directory, report))
        warnings.extend(report["warnings"])
        attached.extend(dict(device, part=part) for device in report["devices"])
        records.extend(dict(record, log=f"parts/{part}/{record['log']}", part=part) for record in report["checks"])
    if parts:
        baseline = parts[0][2]["environment"]
        for part, _, report in parts:
            if any(report["environment"].get(key) != baseline.get(key) for key in ("java", "gradle", "agp", "kotlin")):
                failures.append(f"{part}: incompatible toolchain evidence")
    else:
        baseline = {}
    paths, worst = [], 1 if failures else 0
    for identifier, required in plan["tickets"].items():
        run_id = f"{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}-{uuid.uuid4().hex[:8]}"
        directory = root / runner.REPORT_DIR / identifier / run_id
        directory.mkdir(parents=True)
        sources = []
        for part, source, _ in parts:
            shutil.copytree(source, directory / "parts" / part)
            sources.append({"part": part, "report": f"parts/{part}/part.json", "sha256": hashlib.sha256((source / "part.json").read_bytes()).hexdigest()})
        snapshot = dict(before, acceptance=fingerprint.ticket_acceptance(root, identifier) or hashlib.sha256(b"").hexdigest())
        code, path = runner.finish_run(
            root, identifier, required, plan["gate"], snapshot, baseline, attached, records,
            list(failures), list(warnings), min((r["started_at"] for _, _, r in parts), default=runner.now()),
            run_id, directory, stream, execution={"mode": "distributed", "context": plan, "parts": sources},
            coverage=plan["coverage"],
        )
        paths.append(path)
        worst = max(worst, code)
    return worst, paths
