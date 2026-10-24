import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import fingerprint
import runner
from support import epic, git, make_repo, ticket, write, write_epic

SUITE = '<testsuite name="s" tests="{tests}" failures="{failures}" errors="0" skipped="0"/>'
LINT = '<issues><issue severity="Warning"/><issue severity="Warning"/></issues>'
DEVICE = {"serial": "emulator-5580", "api": "30", "model": "sdk_gphone_x86_64"}


class Harness:
    """Stands in for Gradle/Python processes and writes the result files they would."""

    def __init__(self, root):
        self.root = Path(root)
        self.executed = []
        self.exit_codes = {}
        self.host_tests = 1
        self.host_failures = 0
        self.write_device_results = True
        self.missing_device_modules = set()
        self.during = None

    def execute(self, argv, root, log_path):
        name = Path(log_path).stem
        self.executed.append(name)
        Path(log_path).write_text("log\n", encoding="utf-8")
        if name == "gradle-host":
            if self.host_tests:
                write(self.root, "app/build/test-results/testDebugUnitTest/TEST-a.xml",
                      SUITE.format(tests=self.host_tests, failures=self.host_failures))
            write(self.root, "app/build/reports/lint-results-debug.xml", LINT)
        if name.startswith("gradle-device") and self.write_device_results:
            for module in ("data", "design-system", "app", "catalog"):
                if f":{module}:connectedDebugAndroidTest" in argv and module not in self.missing_device_modules:
                    write(self.root, f"{module}/build/outputs/androidTest-results/connected/debug/TEST-d.xml",
                          SUITE.format(tests=1, failures=0))
        if self.during:
            self.during(name)
        return self.exit_codes.get(name, 0)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        device_env = patch.dict(os.environ, {"ANDROID_SERIAL": ""})
        device_env.start()
        self.addCleanup(device_env.stop)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = make_repo(directory.name)
        self.harness = Harness(self.root)
        self.devices = []
        self.environment = lambda root: {"java": "openjdk 25.0.2"}

    def run_ticket(self, identifier="SELLO-004", gate=None):
        self.stream = io.StringIO()
        return runner.run(
            identifier, self.root, requested_gate=gate, execute=self.harness.execute,
            environment=self.environment, devices=lambda: self.devices, stream=self.stream,
        )

    def report(self, path):
        return json.loads(Path(path).read_text(encoding="utf-8"))

    def set_epic(self, *tickets):
        write_epic(self.root, epic(*tickets))
        git(self.root, "add", "-A")
        git(self.root, "commit", "-qm", "tickets")

    def assert_refused(self, result, message):
        code, path = result
        self.assertNotEqual(0, code)
        self.assertIsNone(path)
        self.assertEqual([], self.harness.executed)
        self.assertIn(message, self.stream.getvalue())
        self.assertFalse((self.root / runner.REPORT_DIR).exists())

    def test_passing_run_writes_a_complete_report(self):
        code, path = self.run_ticket()
        report = self.report(path)
        self.assertEqual(0, code)
        self.assertEqual(2, report["schema_version"])
        self.assertEqual("SELLO-004", report["ticket"])
        self.assertEqual({"required": "G1", "executed": "G1"}, report["gate"])
        self.assertEqual("passed", report["outcome"])
        self.assertEqual(git(self.root, "rev-parse", "HEAD").strip(), report["snapshot"]["head"])
        self.assertFalse(report["snapshot"]["dirty"])
        self.assertEqual(fingerprint.input_fingerprint(self.root), report["snapshot"]["fingerprint"])
        self.assertEqual(
            ["tickets-check", "quality-tests", "performance-tests", "architecture", "links", "gradle-host"],
            [check["name"] for check in report["checks"]],
        )
        host = report["checks"][-1]
        self.assertEqual(0, host["exit_code"])
        self.assertEqual(1, host["results"]["tests"])
        self.assertEqual(2, host["lint"]["warnings"])
        self.assertTrue((Path(path).parent / host["log"]).is_file())
        self.assertEqual({"java": "openjdk 25.0.2"}, report["environment"])
        self.assertLessEqual(report["started_at"], report["finished_at"])
        self.assertEqual(self.root / runner.REPORT_DIR / "SELLO-004" / report["run_id"], Path(path).parent)

    def test_each_run_has_a_unique_id(self):
        first = self.report(self.run_ticket()[1])["run_id"]
        second = self.report(self.run_ticket()[1])["run_id"]
        self.assertNotEqual(first, second)

    def test_report_records_dirty_files_without_machine_paths(self):
        write(self.root, "app/src/Extra.kt", "class Extra\n")
        code, path = self.run_ticket()
        report = self.report(path)
        self.assertEqual(0, code)
        self.assertTrue(report["snapshot"]["dirty"])
        self.assertEqual(["app/src/Extra.kt"], report["snapshot"]["dirty_files"])
        self.assertNotIn(str(self.root), Path(path).read_text(encoding="utf-8"))

    def test_unknown_ticket_is_refused(self):
        self.assert_refused(self.run_ticket("SELLO-999"), "Unknown ticket")

    def test_unfinished_prerequisite_is_refused(self):
        self.set_epic(ticket("SELLO-003", status="Review"),
                      ticket("SELLO-004", status="Backlog", dependencies="SELLO-003"))
        self.assert_refused(self.run_ticket(), "SELLO-003 is Review")

    def test_device_ticket_runs_the_host_gate_locally_by_default(self):
        self.set_epic(ticket("SELLO-003", status="Done"),
                      ticket("SELLO-004", dependencies="SELLO-003", gate="G2"))
        code, path = self.run_ticket()  # no device attached: none is needed
        report = self.report(path)
        self.assertEqual(0, code)
        self.assertEqual({"required": "G2", "executed": "G1"}, report["gate"])
        self.assertEqual("gradle-host", report["checks"][-1]["name"])
        self.assertNotIn("gradle-device", self.harness.executed)
        self.assertEqual([], report["devices"])
        self.assertEqual([], report["coverage"]["device_modules"])

    def test_gate_cannot_be_downgraded_below_the_local_requirement(self):
        self.set_epic(ticket("SELLO-003", status="Done"),
                      ticket("SELLO-004", dependencies="SELLO-003", gate="G2"))
        self.assert_refused(self.run_ticket(gate="G0"), "requires at least G1")

    def test_release_gate_stays_whole_locally(self):
        self.set_epic(ticket("SELLO-003", status="Done"),
                      ticket("SELLO-004", dependencies="SELLO-003", gate="G3"))
        self.devices = [DEVICE]
        self.assert_refused(self.run_ticket(gate="G2"), "requires at least G3")

    def test_higher_gate_is_permitted(self):
        self.devices = [DEVICE]
        code, path = self.run_ticket(gate="G2")
        report = self.report(path)
        self.assertEqual(0, code)
        self.assertEqual({"required": "G1", "executed": "G2"}, report["gate"])
        self.assertEqual("gradle-device", report["checks"][-1]["name"])
        self.assertEqual([DEVICE], report["devices"])

    def test_bootstrap_gate_runs_at_the_minimum_profile(self):
        self.set_epic(ticket("SELLO-003", status="Done", gate="G0"),
                      ticket("SELLO-004", dependencies="SELLO-003"))
        code, path = self.run_ticket("SELLO-003")
        self.assertEqual(0, code)
        self.assertEqual({"required": "G1", "executed": "G1"}, self.report(path)["gate"])

    def test_device_gate_without_device_is_refused(self):
        self.set_epic(ticket("SELLO-003", status="Done"),
                      ticket("SELLO-004", dependencies="SELLO-003", gate="G2"))
        self.assert_refused(self.run_ticket(gate="G2"), "no connected device")

    def test_release_gate_without_device_is_refused(self):
        self.set_epic(ticket("SELLO-003", status="Done"),
                      ticket("SELLO-004", dependencies="SELLO-003", gate="G3"))
        self.assert_refused(self.run_ticket(), "no connected device")

    def test_missing_toolchain_is_refused(self):
        def missing(root):
            raise runner.Refusal("ANDROID_HOME is not set")
        self.environment = missing
        self.assert_refused(self.run_ticket(), "ANDROID_HOME is not set")

    def test_failed_check_fails_the_run_and_still_runs_the_rest(self):
        self.harness.exit_codes["quality-tests"] = 1
        code, path = self.run_ticket()
        report = self.report(path)
        self.assertNotEqual(0, code)
        self.assertEqual("failed", report["outcome"])
        self.assertEqual(6, len(self.harness.executed))
        self.assertIn("quality-tests exited 1", report["failures"])

    def test_host_gate_without_test_results_fails(self):
        self.harness.host_tests = 0
        code, path = self.run_ticket()
        self.assertNotEqual(0, code)
        self.assertIn("gradle-host produced no test results", self.report(path)["failures"])

    def test_failing_tests_in_results_fail_even_with_zero_exit(self):
        self.harness.host_failures = 1
        code, path = self.run_ticket()
        self.assertNotEqual(0, code)
        self.assertIn("gradle-host reported 1 failing tests", self.report(path)["failures"])

    def test_stale_device_results_do_not_count(self):
        self.devices = [DEVICE]
        stale = "app/build/outputs/androidTest-results/connected/debug/TEST-d.xml"
        write(self.root, stale, SUITE.format(tests=1, failures=0))
        os.utime(self.root / stale, (1, 1))
        self.harness.write_device_results = False
        code, path = self.run_ticket(gate="G2")
        self.assertNotEqual(0, code)
        self.assertIn("gradle-device produced no test results", self.report(path)["failures"])

    def test_device_gate_fails_when_one_module_produces_no_fresh_results(self):
        self.devices = [DEVICE]
        self.harness.missing_device_modules = {"data"}
        code, path = self.run_ticket(gate="G2")
        self.assertGreater(self.report(path)["checks"][-1]["results"]["tests"], 0)
        self.assertNotEqual(0, code)
        self.assertIn("gradle-device: data produced no fresh test results", self.report(path)["failures"])

    def test_inputs_changed_during_the_run_fail(self):
        def edit(name):
            if name == "gradle-host":
                write(self.root, "app/src/Main.kt", "fun main() = println(2)\n")
        self.harness.during = edit
        code, path = self.run_ticket()
        self.assertNotEqual(0, code)
        self.assertIn("inputs changed while checks were running", self.report(path)["failures"])


if __name__ == "__main__":
    unittest.main()


class DefaultDevicesTests(unittest.TestCase):
    """Which devices the runner asks about before a device gate, and for how long."""

    LISTING = "List of devices attached\nR5GYC4FA50N\tdevice\nemulator-5740\tdevice\n\n"

    def setUp(self):
        self.asked = []
        which = patch.object(runner.shutil, "which", return_value="/sdk/adb")
        which.start()
        self.addCleanup(which.stop)
        self.silent = set()

    def run_adb(self, argv, **kwargs):
        if argv[1:] == ["devices"]:
            return runner.subprocess.CompletedProcess(argv, 0, self.LISTING, "")
        serial = argv[2]
        self.asked.append(serial)
        if serial in self.silent:
            raise runner.subprocess.TimeoutExpired(argv, kwargs.get("timeout"))
        value = {"ro.build.version.sdk": "30", "ro.product.model": "model"}[argv[-1]]
        return runner.subprocess.CompletedProcess(argv, 0, value + "\n", "")

    def devices(self, serial):
        with patch.dict(os.environ, {"ANDROID_SERIAL": serial}), \
                patch.object(runner.subprocess, "run", side_effect=self.run_adb):
            return runner.default_devices()

    def test_with_a_selected_device_no_other_device_is_asked(self):
        devices = self.devices("emulator-5740")

        self.assertEqual([{"serial": "emulator-5740", "api": "30", "model": "model"}], devices)
        self.assertEqual({"emulator-5740"}, set(self.asked))

    def test_without_a_selection_every_device_is_still_asked(self):
        devices = self.devices("")

        self.assertEqual(["R5GYC4FA50N", "emulator-5740"], [d["serial"] for d in devices])
        self.assertEqual({"R5GYC4FA50N", "emulator-5740"}, set(self.asked))

    def test_a_device_that_does_not_answer_is_reported_without_an_api_instead_of_waiting(self):
        self.silent = {"emulator-5740"}

        devices = self.devices("emulator-5740")

        self.assertEqual([{"serial": "emulator-5740", "api": "", "model": ""}], devices)

