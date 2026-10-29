"""The documented gates must be the gates scripts/check runs."""
import re
import unittest
from pathlib import Path
from unittest.mock import patch

import gates

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "docs/development/quality-gates.md"


def java_hash(text):
    """String.hashCode, as the instrumentation runner's ShardingFilter reads a test's name."""
    value = 0
    for character in text:
        value = (31 * value + ord(character)) & 0xFFFFFFFF
    return value - (1 << 32) if value >= 1 << 31 else value


def pull_request_tests(module):
    """'method(package.Class)' for each device test a pull request runs (parameters aside)."""
    def marked(lines, at):
        return any(line.strip() == "@NightlyOnly" for line in lines[max(0, at - 4):at])

    tests = []
    for source in sorted((ROOT / module / "src").glob("androidTest*/**/*.kt")):
        lines = source.read_text(encoding="utf-8").splitlines()
        package = next(line.split()[1] for line in lines if line.startswith("package "))
        owner, nightly = None, False
        for at, line in enumerate(lines):
            declared = re.match(r"(?:abstract |open )?class (\w+)", line)
            if declared:
                owner, nightly = declared.group(1), marked(lines, at)
            test = re.match(r"\s*(?:@Test\s+)?fun (\w+)", line)
            if test and owner and not nightly and not marked(lines, at) and (
                    "@Test" in line or any(lines[k].strip() == "@Test" for k in range(max(0, at - 3), at))):
                tests.append(f"{test.group(1)}({package}.{owner})")
    return tests


class GateTests(unittest.TestCase):
    def test_documented_host_commands_match_the_gate(self):
        text = GUIDE.read_text(encoding="utf-8")
        block = re.search(r"```bash\n(\./gradlew ktlintCheck[^\n]*\n\./gradlew :domain:test[\s\S]*?)```", text).group(1)
        first, second = block.replace("\\\n", " ").strip().split("\n")
        self.assertEqual(" ".join(gates.gradle(*gates.FORMAT_TASKS)), first.strip())
        self.assertEqual(list(gates.gradle(*gates.HOST_TASKS)), second.split())
        for name, _ in gates.FAST_CHECKS:
            self.assertIn(f"`{name}`", text)

    def test_host_gate_covers_every_module_lint_and_both_builds(self):
        for task in (":domain:test", ":data:testDebugUnitTest", ":design-system:testDebugUnitTest",
                     ":app:testDebugUnitTest", "lintDebug", ":app:assembleRelease", ":app:assembleDebug"):
            self.assertIn(task, gates.HOST_TASKS)
        self.assertEqual(("ktlintCheck",), gates.FORMAT_TASKS)
        self.assertEqual(["tickets", "quality-tests", "performance-tests", "architecture", "links", "licences"],
                         [name for name, _ in gates.FAST_CHECKS])

    def test_every_host_task_is_in_exactly_one_part(self):
        self.assertEqual(["tests", "lint", "assemble"], list(gates.HOST_PARTS))
        tasks = [task for part in gates.HOST_PARTS.values() for task in part]
        self.assertEqual(len(tasks), len(set(tasks)))
        self.assertEqual(gates.HOST_TASKS, tuple(tasks))
        self.assertTrue(all(task.endswith(("test", "UnitTest", "verifyRoborazziDebug")) for task in gates.HOST_PARTS["tests"]))
        self.assertEqual(("lintDebug",), gates.HOST_PARTS[gates.FORMAT_PART])
        # The release checks read the release APK, so it is built by the part that runs them.
        self.assertEqual((":app:assembleDebug", ":app:assembleRelease"), gates.HOST_PARTS[gates.RELEASE_PART])

    def test_host_gate_compares_the_screenshots_with_their_baselines(self):
        for module in ("app", "design-system"):
            self.assertIn(f":{module}:verifyRoborazziDebug", gates.HOST_TASKS)
            self.assertNotIn(f":{module}:recordRoborazziDebug", gates.HOST_TASKS)

    def test_ci_release_checks_sign_with_a_throwaway_key_and_never_claim_production(self):
        self.assertEqual(["release-licenses", "release-ephemeral"], [name for name, _ in gates.release_checks(ci=True)])
        local = gates.release_checks(ci=False)
        self.assertEqual(["release-licenses", "release-production", "release-launch"], [name for name, _ in local])
        self.assertIn("production", local[1][1])

    def test_the_app_suite_runs_whole_on_one_runner_and_the_rest_as_one_group(self):
        self.assertEqual(["app", "support"], list(gates.DEVICE_GROUPS))
        gates.validate_device_groups()
        self.assertEqual(("./gradlew", ":app:connectedDebugAndroidTest",
                          "-Pandroid.testInstrumentationRunnerArguments.numShards=2",
                          "-Pandroid.testInstrumentationRunnerArguments.shardIndex=1", "--continue"),
                         gates.device_tests(("app",), (1, 2), scope="nightly"))
        self.assertNotIn("numShards", " ".join(gates.device_tests(("app",), gates.DEVICE_GROUPS["app"].shard)))
        self.assertNotIn("numShards", " ".join(gates.device_tests(("data", "design-system"))))
        self.assertEqual(("./gradlew", "ktlintCheck", ":data:assembleDebugAndroidTest", "--continue"),
                         gates.device_build(("data",)))

    def test_a_pull_request_leaves_out_the_nightly_only_app_tests_and_the_nightly_runs_them(self):
        leave_out = f"-Pandroid.testInstrumentationRunnerArguments.notAnnotation={gates.NIGHTLY_ONLY}"
        self.assertEqual(("./gradlew", ":app:connectedDebugAndroidTest", leave_out, "--continue"),
                         gates.device_tests(("app",)))
        self.assertEqual(("./gradlew", ":app:connectedDebugAndroidTest", "--continue"),
                         gates.device_tests(("app",), scope="nightly"))
        # Only the app suite has the annotation.
        self.assertNotIn(leave_out, gates.device_tests(("data", "design-system")))
        with self.assertRaises(ValueError):
            gates.device_tests(("app",), scope="everything")

    def test_the_nightly_only_annotation_is_the_class_the_argument_names(self):
        # A missing class is ignored by the runner, so a moved annotation would quietly
        # run the nightly tests on every pull request.
        source = ROOT / gates.NIGHTLY_ONLY_SOURCE
        package, name = gates.NIGHTLY_ONLY.rsplit(".", 1)
        text = source.read_text(encoding="utf-8")
        self.assertIn(f"package {package}\n", text)
        self.assertIn(f"annotation class {name}\n", text)
        self.assertIn("AnnotationRetention.RUNTIME", text)
        self.assertEqual(f"{name}.kt", source.name)
        self.assertEqual(package.replace(".", "/"), str(source.parent).split("/java/", 1)[1])
        self.assertEqual(("app",), gates.NIGHTLY_ONLY_MODULES)

    def test_every_device_group_keeps_tests_on_a_pull_request(self):
        # A group whose selected suite leaves no results fails its job by design, so the
        # tests left out on a pull request must never be all of a group's tests.
        for name, group in gates.DEVICE_GROUPS.items():
            for module in group.modules:
                with self.subTest(group=name, module=module):
                    kept = pull_request_tests(module)
                    if group.shard:
                        index, count = group.shard
                        kept = [test for test in kept if abs(java_hash(test)) % count == index]
                    self.assertTrue(kept, f"{name} would run no {module} device tests on a pull request")

    def test_an_incomplete_or_inconsistent_shard_split_is_refused(self):
        support = gates.DEVICE_GROUPS["support"]
        for name, groups in (
            ("missing shard", {"app-1": gates.Group(("app",), (0, 2)), "support": support}),
            ("same shard twice", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (0, 2)), "support": support}),
            ("different counts", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (1, 3)), "support": support}),
            ("whole run and a shard", {"app-1": gates.Group(("app",)), "app-2": gates.Group(("app",), (1, 2)), "support": support}),
            ("selection map disagrees", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (1, 2)),
                                         "data": gates.Group(("data",)), "design-system": gates.Group(("design-system",))}),
        ):
            with self.subTest(name=name), patch.dict(gates.DEVICE_GROUPS, groups, clear=True):
                with self.assertRaises(ValueError):
                    gates.validate_device_groups()

    def test_lint_errors_fail_the_build_because_nothing_relaxes_lint(self):
        # Android lint aborts on errors by default; the gate relies on Gradle's exit code.
        root = GUIDE.parents[2]
        relaxing = re.compile(r"abortOnError\s*=\s*false|isAbortOnError\s*=\s*false|ignoreFailures\s*=\s*true"
                              r"|baseline\s*[=(]|ignoreWarnings\s*=\s*true|checkReleaseBuilds\s*=\s*false")
        builds = [*root.glob("*.gradle.kts"), *root.glob("*/build.gradle.kts"), *root.glob("gradle/*.kts")]
        self.assertTrue(builds)
        for build in builds:
            with self.subTest(build=build.name):
                self.assertIsNone(relaxing.search(build.read_text(encoding="utf-8")))
        self.assertEqual([], [p for p in root.glob("**/lint*.xml") if "build" not in p.parts])

    def test_unit_test_tasks(self):
        self.assertEqual(":domain:test", gates.unit_test_task("domain"))
        self.assertEqual(":app:testDebugUnitTest", gates.unit_test_task("app"))


if __name__ == "__main__":
    unittest.main()
