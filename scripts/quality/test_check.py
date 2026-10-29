"""scripts/check: argument handling, stage order, fail-fast and the result checks of each job."""
import contextlib
import io
import json
import os
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

import check

SETTINGS = "".join(f'include(":{module}")\n' for module in ("app", "catalog", "data", "design-system", "domain", "prototype"))
SUITE = ('<testsuite name="{name}" tests="{tests}" failures="{failures}" errors="0" skipped="{skipped}">'
         '{cases}</testsuite>')
FAILING_CASE = '<testcase name="breaks" classname="com.software.sello.{name}"><failure message="boom">trace</failure></testcase>'


class Fake:
    """Stands in for Gradle and the Python checks, and writes the results Gradle would."""

    def __init__(self, root):
        self.root = Path(root)
        self.ran = []
        self.exit_codes = {}  # task or check name -> exit code
        self.host_tests, self.host_failures = 2, 0
        self.missing_device_modules, self.skipped_device_modules = set(), set()

    def write(self, relative, tests=1, failures=0, skipped=0, name="Suite"):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(SUITE.format(name=name, tests=tests, failures=failures, skipped=skipped,
                                     cases=FAILING_CASE.format(name=name) * failures), encoding="utf-8")

    def __call__(self, argv, root, environment):
        self.ran.append(argv)
        self.serial = environment.get("ANDROID_SERIAL")
        if ":domain:test" in argv and self.host_tests:
            self.write("domain/build/test-results/test/TEST-host.xml", self.host_tests, self.host_failures, name="HostTest")
        for module in ("data", "design-system", "app", "catalog"):
            if f":{module}:connectedDebugAndroidTest" in argv and module not in self.missing_device_modules:
                self.write(f"{module}/build/outputs/androidTest-results/connected/debug/TEST-device.xml",
                           skipped=int(module in self.skipped_device_modules), name=f"{module}DeviceTest")
        return next((code for key, code in self.exit_codes.items() if key in argv), 0)

    def names(self):
        """Each command as a short name: a Python check, a Gradle task list, or a release check."""
        names = []
        for argv in self.ran:
            if argv[0] == "./gradlew":
                names.append(" ".join(task for task in argv[1:] if task != "--continue"))
            else:
                path = next(part for part in argv[1:] if "/" in part)
                names.append(path.split("/")[-1] if path.endswith(".py") else path)
        return names


class CheckTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        (self.root / "settings.gradle.kts").write_text(SETTINGS, encoding="utf-8")
        self.fake = Fake(self.root)
        self.summary = self.root / "summary.md"
        self.environ = {"GITHUB_STEP_SUMMARY": str(self.summary)}
        self.attached, self.api = ["emulator-5580"], "30"

    def check(self, *argv, environ=None):
        self.out = io.StringIO()
        return check.main(list(argv), root=self.root, execute=self.fake, out=self.out,
                          environ=self.environ if environ is None else environ,
                          devices=lambda root: self.attached, api=lambda root, serial: self.api)

    def usage_error(self, *argv):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            self.check(*argv)
        self.assertEqual(2, raised.exception.code)
        self.assertEqual([], self.fake.ran)

    # Arguments

    def test_mixed_or_missing_modes_are_usage_errors(self):
        for argv in ((), ("app", "--host"), ("--host", "--fast"), ("--tests", "X"), ("--lint", "--host"),
                     ("--ci-release",), ("app", "--ci-release"), ("--modules", "app", "--host"), ("--build-only", "--fast"),
                     ("--event", "push", "--host"), ("--device", "app-3"), ("--plan",)):
            with self.subTest(argv=argv):
                self.usage_error(*argv)

    def test_unknown_modules_are_refused_before_anything_runs(self):
        self.assertEqual(2, self.check("app", "ui"))
        self.assertIn("REFUSED: unknown modules ui", self.out.getvalue())
        self.assertEqual(2, self.check("--device", "support", "--modules", "domain"))
        self.assertEqual([], self.fake.ran)

    def test_modules_run_ktlint_and_their_unit_tests_with_an_optional_filter_and_lint(self):
        self.assertEqual(0, self.check("domain", "app"))
        self.assertEqual([":domain:ktlintCheck :domain:test :app:ktlintCheck :app:testDebugUnitTest"], self.fake.names())
        self.fake.ran.clear()
        self.assertEqual(0, self.check("domain", "data", "--tests", "*Money*", "--lint"))
        self.assertEqual([":domain:ktlintCheck :domain:test --tests *Money* "
                          ":data:ktlintCheck :data:testDebugUnitTest --tests *Money* :data:lintDebug"], self.fake.names())

    def test_a_failing_module_test_fails_and_is_named(self):
        self.fake.host_failures = 1
        self.assertEqual(1, self.check("domain"))
        self.assertIn("FAILED TEST: com.software.sello.HostTest.breaks", self.out.getvalue())
        self.assertIn("`com.software.sello.HostTest.breaks` | boom", self.summary.read_text(encoding="utf-8"))

    # Host

    def test_fast_runs_every_python_check_and_no_gradle(self):
        self.fake.exit_codes = {"scripts/quality/tickets.py": 1}
        self.assertEqual(1, self.check("--fast"))
        self.assertEqual(["tickets.py", "scripts/quality", "scripts/performance", "architecture.py", "links.py"], self.fake.names())
        self.assertIn("`tickets`", self.summary.read_text(encoding="utf-8"))

    def test_host_runs_cheap_checks_first_then_ktlint_then_the_gradle_host_gate(self):
        self.assertEqual(0, self.check("--host"))
        names = self.fake.names()
        self.assertEqual(["tickets.py", "scripts/quality", "scripts/performance", "architecture.py", "links.py", "ktlintCheck"], names[:6])
        self.assertTrue(names[6].startswith(":domain:test :data:testDebugUnitTest"))
        self.assertEqual(7, len(names))
        self.assertIn("### Host tests: 2 passed, 0 failed, 0 skipped", self.summary.read_text(encoding="utf-8"))

    def test_a_failed_cheap_stage_stops_before_gradle(self):
        for failing, ran in (("links.py", 5), ("ktlintCheck", 6)):
            with self.subTest(failing=failing):
                self.fake.ran.clear()
                self.fake.exit_codes = {"scripts/quality/links.py" if failing == "links.py" else failing: 1}
                self.assertEqual(1, self.check("--host"))
                self.assertEqual(ran, len(self.fake.ran))

    def test_host_fails_without_test_results_or_with_failing_tests_even_on_a_zero_exit(self):
        self.fake.host_tests = 0
        self.assertEqual(1, self.check("--host"))
        self.assertIn("produced no test results", self.out.getvalue())
        self.fake.host_tests, self.fake.host_failures = 2, 1
        self.assertEqual(1, self.check("--host"))
        self.assertIn("1 host tests failed", self.out.getvalue())

    def test_ci_release_adds_licences_and_throwaway_signing_after_the_host_gate(self):
        self.assertEqual(0, self.check("--host", "--ci-release"))
        self.assertEqual(["licenses.py", "release.py"], self.fake.names()[-2:])
        self.assertEqual("ephemeral", self.fake.ran[-1][-1])

    # Devices

    def test_device_runs_need_a_named_api_30_device_and_refuse_before_building(self):
        self.assertEqual(2, self.check("--device"))
        self.assertIn("ANDROID_SERIAL", self.out.getvalue())
        self.assertEqual(2, self.check("--device", environ={"ANDROID_SERIAL": "emulator-5590"}))
        self.api = "34"
        self.assertEqual(2, self.check("--device", environ={"ANDROID_SERIAL": "emulator-5580"}))
        self.assertEqual([], self.fake.ran)

    def test_on_ci_the_only_attached_emulator_is_used(self):
        self.assertEqual(0, self.check("--device", "support", environ={"GITHUB_ACTIONS": "true"}))
        self.assertEqual("emulator-5580", self.fake.serial)
        self.attached = ["emulator-5580", "emulator-5582"]
        self.assertEqual(2, self.check("--device", "support", environ={"GITHUB_ACTIONS": "true"}))

    def test_build_only_formats_and_builds_the_selected_test_apks_without_a_device(self):
        self.attached = []
        self.assertEqual(0, self.check("--device", "support", "--modules", "data,app", "--build-only"))
        self.assertEqual(["ktlintCheck :data:assembleDebugAndroidTest"], self.fake.names())

    def test_a_hosted_group_runs_its_shard_of_the_selected_modules_after_building(self):
        environ = {"ANDROID_SERIAL": "emulator-5580"}
        self.assertEqual(0, self.check("--device", "app-2", "--modules", "data,app", environ=environ))
        self.assertEqual(["ktlintCheck :app:assembleDebugAndroidTest",
                          ":app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.numShards=2 "
                          "-Pandroid.testInstrumentationRunnerArguments.shardIndex=1"], self.fake.names())
        self.assertEqual("emulator-5580", self.fake.serial)
        self.fake.ran.clear()
        self.assertEqual(0, self.check("--device", "support", "--modules", "catalog", environ=environ))
        self.assertEqual(":catalog:connectedDebugAndroidTest", self.fake.names()[-1])
        self.assertEqual(2, self.check("--device", "app-1", "--modules", "catalog", environ=environ))

    def test_locally_every_suite_runs_whole_on_one_emulator(self):
        self.assertEqual(0, self.check("--device", environ={"ANDROID_SERIAL": "emulator-5580"}))
        self.assertEqual(":data:connectedDebugAndroidTest :design-system:connectedDebugAndroidTest "
                         ":app:connectedDebugAndroidTest :catalog:connectedDebugAndroidTest", self.fake.names()[-1])

    def test_a_selected_suite_without_fresh_results_or_with_skips_fails(self):
        environ = {"ANDROID_SERIAL": "emulator-5580"}
        self.fake.write("catalog/build/outputs/androidTest-results/connected/debug/TEST-device.xml")
        stale = self.root / "catalog/build/outputs/androidTest-results/connected/debug/TEST-device.xml"
        old = time.time_ns() - 60_000_000_000
        os.utime(stale, ns=(old, old))
        self.fake.missing_device_modules = {"catalog"}
        self.assertEqual(1, self.check("--device", "support", environ=environ))
        self.assertIn("catalog produced no fresh device test results", self.out.getvalue())
        self.fake.missing_device_modules, self.fake.skipped_device_modules = set(), {"data"}
        self.assertEqual(1, self.check("--device", "support", environ=environ))
        self.assertIn("data skipped 1 device tests", self.out.getvalue())

    # Release

    def test_release_refuses_without_a_device_before_anything_runs(self):
        self.assertEqual(2, self.check("--release"))
        self.assertEqual([], self.fake.ran)

    def test_release_runs_the_whole_gate_then_the_owners_signing_and_launch(self):
        self.assertEqual(0, self.check("--release", environ={"ANDROID_SERIAL": "emulator-5580"}))
        names = self.fake.names()
        self.assertEqual("ktlintCheck", names[5])
        self.assertTrue(names[6].startswith(":domain:test"))
        self.assertEqual("ktlintCheck :data:assembleDebugAndroidTest :design-system:assembleDebugAndroidTest "
                         ":app:assembleDebugAndroidTest :catalog:assembleDebugAndroidTest", names[7])
        self.assertIn(":app:connectedDebugAndroidTest", names[8])
        self.assertNotIn("numShards", names[8])
        self.assertEqual(["licenses.py", "release.py", "release.py"], names[9:])
        self.assertEqual(["check", "production", "launch"], [argv[-1] for argv in self.fake.ran[9:]])

    # Plan

    def test_plan_prints_json_and_writes_the_github_outputs(self):
        subprocess.run(["git", "init", "-q", "-b", "main", str(self.root)], check=True)
        for setting in (("user.name", "Test"), ("user.email", "test@example.invalid")):
            subprocess.run(["git", "-C", str(self.root), "config", *setting], check=True)
        subprocess.run(["git", "-C", str(self.root), "add", "-A"], check=True)
        subprocess.run(["git", "-C", str(self.root), "commit", "-qm", "initial"], check=True)
        (self.root / "domain/src/main").mkdir(parents=True)
        (self.root / "domain/src/main/Money.kt").write_text("class Money\n", encoding="utf-8")
        output = self.root / "output"
        self.environ["GITHUB_OUTPUT"] = str(output)
        printed = io.StringIO()
        with contextlib.redirect_stdout(printed):
            self.assertEqual(0, self.check("--plan", "HEAD"))
        self.assertEqual("G2", json.loads(printed.getvalue())["gate"])
        self.assertEqual("gate=G2\nhost=true\nrelease=false\ndevice=true\n"
                         'groups=["app-1", "app-2", "support"]\nmodules=data,app\n', output.read_text(encoding="utf-8"))
        self.assertIn("### Plan: G2 (pull_request)", self.summary.read_text(encoding="utf-8"))
        self.assertEqual([], self.fake.ran)


if __name__ == "__main__":
    unittest.main()
