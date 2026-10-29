"""What each gate runs. scripts/check executes it, locally and in the Quality workflow.

G0  the Python/Git checks (FAST_CHECKS), including the release licence list.
G1  G0, then ktlint, then every host test, the screenshot baselines, lint and the
    assemblies (HOST_TASKS).
G2  G1 plus the device suites of the selected modules on an API-30 emulator.
G3  every device suite, then the release checks (ADR 0016): the owner's production
    signing locally, a throwaway key on CI.

A pull request's device run leaves out the app tests marked @NightlyOnly
(NIGHTLY_ONLY); the nightly, a manual run and the release gate run every test.
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
    ("licences", ("python3", "scripts/quality/licenses.py", "check")),
)
FORMAT_TASKS = ("ktlintCheck",)
HOST_TASKS = (
    ":domain:test", ":data:testDebugUnitTest", ":design-system:testDebugUnitTest",
    ":app:testDebugUnitTest", ":app:verifyRoborazziDebug", ":design-system:verifyRoborazziDebug",
    "lintDebug", ":app:assembleDebug", ":app:assembleRelease",
)
HOST_RESULTS = "*/build/test-results/**/TEST-*.xml"
DEVICE_RESULTS = "{module}/build/outputs/androidTest-results/connected/**/TEST-*.xml"
# Device tests only the nightly and manual runs execute; only the app suite has them.
NIGHTLY_ONLY = "com.software.sello.NightlyOnly"
NIGHTLY_ONLY_SOURCE = "app/src/androidTestDebug/java/com/software/sello/NightlyOnly.kt"
NIGHTLY_ONLY_MODULES = ("app",)
# What a device run executes: "pull-request" leaves out NIGHTLY_ONLY; "nightly" runs all.
SCOPES = ("pull-request", "nightly")


@dataclass(frozen=True)
class Group:
    """One hosted device job: its modules, and which shard of their tests it runs, if split."""
    modules: tuple
    shard: tuple = ()  # (index, count): the instrumentation runner keeps every count-th test


# The app suite runs whole on one runner: its pull-request tests take under two minutes
# on an API-30 emulator (measured 2026-10-29), less than building a second test APK.
# A Group can still split a suite into shards; the data and design-system suites share
# the other runner.
DEVICE_GROUPS = {
    "app": Group(("app",)),
    "support": Group(("data", "design-system")),
}


def unit_test_task(module):
    return ":domain:test" if module == "domain" else f":{module}:testDebugUnitTest"


def gradle(*tasks):
    return ("./gradlew", *tasks, "--continue")


def device_build(modules):
    """Before the emulator boots: formatting, then every selected test APK."""
    return gradle(*FORMAT_TASKS, *(f":{module}:assembleDebugAndroidTest" for module in modules))


def device_tests(modules, shard=(), scope="pull-request"):
    if scope not in SCOPES:
        raise ValueError(f"Unknown device scope {scope!r}")
    arguments = []
    if shard:
        index, count = shard
        arguments += [f"-Pandroid.testInstrumentationRunnerArguments.numShards={count}",
                      f"-Pandroid.testInstrumentationRunnerArguments.shardIndex={index}"]
    # A Gradle property reaches every module of the run; a test APK without the annotation
    # class ignores the argument (androidx.test TestRequestBuilder logs and skips it).
    if scope == "pull-request" and any(module in NIGHTLY_ONLY_MODULES for module in modules):
        arguments.append(f"-Pandroid.testInstrumentationRunnerArguments.notAnnotation={NIGHTLY_ONLY}")
    return gradle(*(f":{module}:connectedDebugAndroidTest" for module in modules), *arguments)


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
