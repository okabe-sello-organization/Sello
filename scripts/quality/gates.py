"""Gate profiles: which checks each gate runs and what results they must leave behind."""
import re

import selection
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path

GATES = ("G0", "G1", "G2", "G3")
MINIMUM_GATE = "G1"


def local_gate(required):
    """The gate a local run executes by default for work that requires [required].

    The hosted run executes the device suites on the real merge result before anything
    merges (ADR 0009), so the local requirement for a G2 ticket is the host gate G1.
    G3 stays whole: only the owner's machine holds the production signing key (ADR 0016).
    G0 stays G0: documentation needs no Gradle.
    """
    return required if required in ("G0", "G3") else MINIMUM_GATE

HOST_TASKS = (
    "ktlintCheck", ":domain:test", ":data:testDebugUnitTest", ":design-system:testDebugUnitTest",
    ":app:testDebugUnitTest", ":catalog:testDebugUnitTest", "lintDebug",
    ":app:assembleDebug", ":app:assembleRelease", ":catalog:assembleDebug",
)
DEVICE_TASKS = (
    ":data:connectedDebugAndroidTest", ":design-system:connectedDebugAndroidTest",
    ":app:connectedDebugAndroidTest", ":catalog:connectedDebugAndroidTest",
)
DEVICE_GROUPS = {
    "app": (":app:connectedDebugAndroidTest",),
    "support": (
        ":data:connectedDebugAndroidTest", ":design-system:connectedDebugAndroidTest",
        ":catalog:connectedDebugAndroidTest",
    ),
}
HOST_RESULTS = "*/build/test-results/**/TEST-*.xml"
DEVICE_RESULTS = "*/build/outputs/androidTest-results/connected/**/TEST-*.xml"
LINT_RESULTS = "*/build/reports/lint-results-debug.xml"


class Unavailable(Exception):
    """A gate cannot be executed with the checks that exist today."""


@dataclass(frozen=True)
class Check:
    name: str
    argv: tuple
    needs_device: bool = False
    results: str = ""
    fresh_results: bool = False
    lint: bool = False
    result_modules: tuple = ()


def device_check(name="gradle-device", tasks=DEVICE_TASKS):
    return Check(
        name, ("./gradlew", *tasks, "--continue"), needs_device=True,
        results=DEVICE_RESULTS, fresh_results=True,
        result_modules=tuple(task.split(":")[1] for task in tasks),
    )


def validate_device_groups():
    grouped = [task for tasks in DEVICE_GROUPS.values() for task in tasks]
    if sorted(grouped) != sorted(DEVICE_TASKS) or len(grouped) != len(set(grouped)):
        raise Unavailable("device groups must cover every device task exactly once")


def release_checks(ci=False):
    """G3's checks after G2, on the release APK the host step built (ADR 0016).

    Locally the owner's key signs it: its certificate must be the pinned one, and the
    signed APK must install and start on the emulator. CI holds no customer key, so it
    signs with a throwaway one and leaves the production proof to the owner's report.
    """
    licenses = Check("release-licenses", ("python3", "scripts/quality/licenses.py", "check"))
    if ci:
        return [licenses, Check("release-ephemeral", ("python3", "scripts/quality/release.py", "ephemeral"))]
    return [
        licenses,
        Check("release-production", ("python3", "scripts/quality/release.py", "production")),
        Check("release-launch", ("python3", "scripts/quality/release.py", "launch"), needs_device=True),
    ]


def plan(gate, ci=False):
    if gate not in GATES:
        raise Unavailable(f"Unknown gate {gate}")
    checks = [
        # Structure only: a run cannot depend on its own final evidence.
        Check("tickets-check", ("python3", "scripts/quality/tickets.py", "check", "--skip-evidence")),
        Check("quality-tests", ("python3", "-m", "unittest", "discover", "-s", "scripts/quality", "-p", "test_*.py")),
        Check("performance-tests", ("python3", "-m", "unittest", "discover", "-s", "scripts/performance", "-p", "test_*.py")),
        Check("architecture", ("python3", "scripts/quality/architecture.py")),
        Check("links", ("python3", "scripts/quality/links.py")),
        Check("gradle-host", ("./gradlew", *HOST_TASKS, "--continue"), results=HOST_RESULTS, lint=True),
    ]
    if gate == "G0":
        return checks[:-1]
    if gate in ("G2", "G3"):
        checks.append(device_check())
    if gate == "G3":
        checks.extend(release_checks(ci))
    return checks


def test_results(root, pattern, newer_than=None):
    totals = {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for path in sorted(Path(root).glob(pattern)):
        if newer_than is not None and path.stat().st_mtime_ns < newer_than:
            continue
        suite = ElementTree.parse(path).getroot()
        totals["suites"] += 1
        for name in ("tests", "failures", "errors", "skipped"):
            totals[name] += int(suite.get(name, 0))
    return totals


def device_results(root, modules, newer_than=None):
    return {
        module: test_results(
            root, DEVICE_RESULTS.replace("*/", f"{module}/", 1), newer_than,
        )
        for module in modules
    }


def sum_results(results):
    return {name: sum(result[name] for result in results) for name in ("suites", "tests", "failures", "errors", "skipped")}


def lint_results(root):
    totals = {"reports": 0, "errors": 0, "warnings": 0}
    for path in sorted(Path(root).glob(LINT_RESULTS)):
        text = path.read_text(encoding="utf-8")
        totals["reports"] += 1
        totals["errors"] += len(re.findall(r'severity="(?:Error|Fatal)"', text))
        totals["warnings"] += len(re.findall(r'severity="Warning"', text))
    return totals
