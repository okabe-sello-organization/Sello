"""What each gate runs. scripts/check executes it, locally and in the Quality workflow.

G0  the Python/Git checks (FAST_CHECKS).
G1  G0, then ktlint, then every host test, lint and the assemblies (HOST_TASKS).
G2  G1 plus the device suites of the selected modules on an API-30 emulator.
G3  every device suite, then the release checks (ADR 0016): the owner's production
    signing locally, a throwaway key on CI.
"""
from dataclasses import dataclass

import selection

GATES = ("G0", "G1", "G2", "G3")
FAST_CHECKS = (
    ("tickets", ("python3", "scripts/quality/tickets.py", "check")),
    ("quality-tests", ("python3", "-m", "unittest", "discover", "-s", "scripts/quality", "-p", "test_*.py")),
    ("performance-tests", ("python3", "-m", "unittest", "discover", "-s", "scripts/performance", "-p", "test_*.py")),
    ("architecture", ("python3", "scripts/quality/architecture.py")),
    ("links", ("python3", "scripts/quality/links.py")),
)
FORMAT_TASKS = ("ktlintCheck",)
HOST_TASKS = (
    ":domain:test", ":data:testDebugUnitTest", ":design-system:testDebugUnitTest",
    ":app:testDebugUnitTest", ":catalog:testDebugUnitTest", "lintDebug",
    ":app:assembleDebug", ":app:assembleRelease", ":catalog:assembleDebug",
)
HOST_RESULTS = "*/build/test-results/**/TEST-*.xml"
DEVICE_RESULTS = "{module}/build/outputs/androidTest-results/connected/**/TEST-*.xml"


@dataclass(frozen=True)
class Group:
    """One hosted device job: its modules, and which shard of their tests it runs, if split."""
    modules: tuple
    shard: tuple = ()  # (index, count): the instrumentation runner keeps every count-th test


# The app suite is the longest by far, so it runs as two shards side by side; the other
# three suites share one runner.
DEVICE_GROUPS = {
    "app-1": Group(("app",), shard=(0, 2)),
    "app-2": Group(("app",), shard=(1, 2)),
    "support": Group(("data", "design-system", "catalog")),
}


def unit_test_task(module):
    return ":domain:test" if module == "domain" else f":{module}:testDebugUnitTest"


def gradle(*tasks):
    return ("./gradlew", *tasks, "--continue")


def device_build(modules):
    """Before the emulator boots: formatting, then every selected test APK."""
    return gradle(*FORMAT_TASKS, *(f":{module}:assembleDebugAndroidTest" for module in modules))


def device_tests(modules, shard=()):
    split = ()
    if shard:
        index, count = shard
        split = (f"-Pandroid.testInstrumentationRunnerArguments.numShards={count}",
                 f"-Pandroid.testInstrumentationRunnerArguments.shardIndex={index}")
    return gradle(*(f":{module}:connectedDebugAndroidTest" for module in modules), *split)


def release_checks(ci):
    """G3's checks after the device suites, on the release APK the host gate built (ADR 0016).

    Locally the owner's key signs it: its certificate must be the pinned one, and the
    signed APK must install and start on the emulator. CI holds no customer key, so it
    signs with a throwaway one and leaves the production proof to the owner's run.
    """
    licences = ("release-licenses", ("python3", "scripts/quality/licenses.py", "check"))
    if ci:
        return (licences, ("release-ephemeral", ("python3", "scripts/quality/release.py", "ephemeral")))
    return (
        licences,
        ("release-production", ("python3", "scripts/quality/release.py", "production")),
        ("release-launch", ("python3", "scripts/quality/release.py", "launch")),
    )


def validate_device_groups():
    """Every device suite runs whole in one group, or once in each shard of one split."""
    covered = {}
    for group in DEVICE_GROUPS.values():
        for module in group.modules:
            covered.setdefault(module, []).append(group.shard or (0, 1))
    if set(covered) != set(selection.MODULES):
        raise ValueError("device groups must cover every device suite")
    for module, shards in covered.items():
        counts = {count for _, count in shards}
        if len(counts) != 1 or sorted(index for index, _ in shards) != list(range(counts.pop())):
            raise ValueError(f"{module}: its groups must be one whole run or every shard of one split exactly once")
    if {name: set(group.modules) for name, group in DEVICE_GROUPS.items()} != \
            {name: set(modules) for name, modules in selection.GROUPS.items()}:
        raise ValueError("device groups and the selection map must name the same split")
