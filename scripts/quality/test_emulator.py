"""scripts/emulator: argument handling, the port it picks and what it refuses to stop."""
import contextlib
import fcntl
import io
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import emulator


class ArgumentsTest(unittest.TestCase):
    def test_commands(self):
        self.assertEqual("start", emulator.parse(["start"]).command)
        self.assertEqual("list", emulator.parse(["list"]).command)
        stop = emulator.parse(["stop", "emulator-5580"])
        self.assertEqual(("stop", "emulator-5580"), (stop.command, stop.serial))

    def test_rejects_a_missing_command_a_missing_serial_and_a_device_that_is_not_an_emulator(self):
        for argv in ([], ["stop"], ["stop", "R58M123ABC"], ["stop", "emulator-5555"],
                     ["stop", "emulator-9000"], ["boot"]):
            with self.subTest(argv=argv), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as exit:
                    emulator.parse(argv)
                self.assertEqual(2, exit.exception.code)


class PortTest(unittest.TestCase):
    def test_skips_attached_emulators_and_ports_in_use(self):
        bound = {5556, 5559}.__contains__
        self.assertEqual(5560, emulator.free_port(["emulator-5554", "R58M123ABC"], bound))

    def test_fails_when_every_port_is_taken(self):
        with self.assertRaises(emulator.Failure):
            emulator.free_port([], lambda port: True)


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/quality.yml"


def emulator_step_settings(text):
    """The emulator step's `with:` keys and values, without its script."""
    lines = text.splitlines()
    step = next(n for n, line in enumerate(lines) if "reactivecircus/android-emulator-runner" in line)
    start = next(n for n in range(step, len(lines)) if lines[n].strip() == "with:")
    indent = len(lines[start]) - len(lines[start].lstrip())
    settings = {}
    for line in lines[start + 1:]:
        if not line.strip() or line.strip().startswith("#"):
            continue
        if len(line) - len(line.lstrip()) <= indent:
            break
        key, _, value = line.strip().partition(":")
        settings[key] = value.strip()
    settings.pop("script")
    return settings


class ConfigTest(unittest.TestCase):
    def test_appends_what_the_ci_action_appends(self):
        path, lines = emulator.avd_config("/avds", "sello_1")
        self.assertEqual("/avds/sello_1.avd/config.ini", str(path))
        self.assertEqual("hw.cpu.ncore=2\n", lines)

    def test_matches_the_workflow_exactly(self):
        # A new key there (ram-size, profile, channel...) must be copied here too.
        self.assertEqual(emulator.WORKFLOW_SETTINGS,
                         emulator_step_settings(WORKFLOW.read_text(encoding="utf-8")))


class Sandbox(unittest.TestCase):
    """A cache directory of its own, an SDK with the image, and no real adb or emulator."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        (root / "sdk/system-images/android-30/google_apis/x86_64").mkdir(parents=True)
        self.adb_calls = []
        self.avd_name = None  # what `emu avd name` answers
        for patcher in (
            mock.patch.dict(os.environ, {"XDG_CACHE_HOME": str(root / "cache"),
                                         "ANDROID_HOME": str(root / "sdk")}),
            mock.patch.object(emulator, "tool", lambda *parts: parts[-1]),
            mock.patch.object(emulator, "adb", self.adb),
            mock.patch.object(emulator.check, "attached_devices", lambda root: []),
            mock.patch.object(emulator, "port_bound", lambda port: False),
            mock.patch.object(emulator.time, "sleep", lambda seconds: None),
            mock.patch.object(emulator.os, "kill"),
        ):
            patcher.start()
            self.addCleanup(patcher.stop)

    def adb(self, serial, *arguments, timeout=30):
        self.adb_calls.append((serial, *arguments))
        if arguments == ("emu", "avd", "name"):
            answer = f"{self.avd_name}\r\nOK\r\n" if self.avd_name else ""
            return subprocess.CompletedProcess([], 0 if self.avd_name else 1, answer, "")
        return subprocess.CompletedProcess([], 0, "", "")

    def lock_is_held(self):
        with (emulator.home() / "lock").open("a") as other:
            try:
                fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                return True
            fcntl.flock(other, fcntl.LOCK_UN)
            return False


def avdmanager(argv, **kwargs):
    name = argv[argv.index("-n") + 1]
    avd = Path(kwargs["env"]["ANDROID_AVD_HOME"]) / f"{name}.avd"
    avd.mkdir(parents=True)
    (avd / "config.ini").write_text("hw.cpu.ncore=4\n", encoding="utf-8")
    return subprocess.CompletedProcess(argv, 0, "", "")


class StartTest(Sandbox):
    def test_chooses_the_port_and_writes_the_record_under_the_lock(self):
        held = []
        free_port = emulator.free_port

        def choosing(attached, bound, reserved):
            held.append(self.lock_is_held())
            return free_port(attached, bound, reserved)

        process = mock.Mock(pid=4242, returncode=1)
        process.poll.return_value = 1

        def spawning(*args, **kwargs):
            held.append(self.lock_is_held())
            return process

        with mock.patch.object(emulator.subprocess, "run", avdmanager), \
                mock.patch.object(emulator, "free_port", choosing), \
                mock.patch.object(emulator.subprocess, "Popen", spawning), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, emulator.main(["start"], out=io.StringIO()))
        self.assertEqual([True, True], held)

    def test_skips_ports_another_start_has_recorded(self):
        with emulator.locked():
            emulator.save({"emulator-5554": {"avd": "sello_other", "home": "/elsewhere", "pid": 1}})
        self.assertEqual(5556, emulator.free_port([], lambda port: False, emulator.records()))

    def test_an_emulator_that_exits_early_is_not_asked_to_quit_on_its_serial(self):
        process = mock.Mock(pid=4242, returncode=1)
        process.poll.return_value = 1
        with emulator.locked():
            emulator.save({"emulator-5556": {"avd": "sello_theirs", "home": "/theirs", "pid": 7}})
        with mock.patch.object(emulator.subprocess, "run", avdmanager), \
                mock.patch.object(emulator.subprocess, "Popen", return_value=process), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(1, emulator.main(["start"], out=io.StringIO()))
        self.assertIn("the emulator exited with 1", err.getvalue())
        self.assertNotIn(("emulator-5554", "emu", "kill"), self.adb_calls)
        emulator.os.kill.assert_not_called()
        # Its own record and AVD are gone; the other agent's record stays.
        self.assertEqual(["emulator-5556"], list(emulator.records()))
        self.assertEqual(["lock", "started.json"], sorted(p.name for p in emulator.home().iterdir()))

    def test_a_boot_that_never_ends_kills_only_its_own_process(self):
        process = mock.Mock(pid=4242, returncode=None)
        process.poll.return_value = None
        with mock.patch.object(emulator.subprocess, "run", avdmanager), \
                mock.patch.object(emulator.subprocess, "Popen", return_value=process), \
                mock.patch.object(emulator, "BOOT_SECONDS", -1), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, emulator.main(["start"], out=io.StringIO()))
        process.kill.assert_called_once()
        self.assertNotIn(("emulator-5554", "emu", "kill"), self.adb_calls)
        self.assertEqual({}, emulator.records())

    def test_no_free_port_deletes_the_new_avd(self):
        with mock.patch.object(emulator.subprocess, "run", avdmanager), \
                mock.patch.object(emulator, "port_bound", lambda port: True), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(1, emulator.main(["start"], out=io.StringIO()))
        self.assertIn("no free emulator port", err.getvalue())
        self.assertEqual([], [p.name for p in emulator.home().iterdir() if p.name.startswith("sello_")])


class StopTest(Sandbox):
    def record(self):
        avd_home = emulator.home() / "sello_mine"
        avd_home.mkdir(parents=True)
        with emulator.locked():
            emulator.save({"emulator-5554": {"avd": "sello_mine", "home": str(avd_home), "pid": 4242}})
        return avd_home

    def test_leaves_an_emulator_it_did_not_start_alone(self):
        with contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(1, emulator.main(["stop", "emulator-5554"], out=io.StringIO()))
        self.assertEqual([], self.adb_calls)
        self.assertIn("was not started by scripts/emulator", err.getvalue())

    def test_another_avd_on_the_port_is_not_stopped(self):
        avd_home = self.record()
        self.avd_name = "someone_else"
        with mock.patch.object(emulator, "runs_avd", return_value=True):
            self.assertEqual(0, emulator.main(["stop", "emulator-5554"], out=io.StringIO()))
        self.assertNotIn(("emulator-5554", "emu", "kill"), self.adb_calls)
        emulator.os.kill.assert_not_called()
        self.assertEqual({}, emulator.records())
        self.assertFalse(avd_home.exists())

    def test_a_reused_process_id_is_not_killed(self):
        self.record()
        self.avd_name = "sello_mine"
        with mock.patch.object(emulator, "runs_avd", return_value=False):
            self.assertEqual(0, emulator.main(["stop", "emulator-5554"], out=io.StringIO()))
        self.assertNotIn(("emulator-5554", "emu", "kill"), self.adb_calls)
        emulator.os.kill.assert_not_called()
        self.assertEqual({}, emulator.records())

    def test_stops_its_own_emulator(self):
        avd_home = self.record()
        self.avd_name = "sello_mine"
        with mock.patch.object(emulator, "runs_avd", return_value=True), \
                mock.patch.object(emulator, "alive", return_value=False):
            self.assertEqual(0, emulator.main(["stop", "emulator-5554"], out=io.StringIO()))
        self.assertIn(("emulator-5554", "emu", "kill"), self.adb_calls)
        self.assertFalse(avd_home.exists())

    def test_reads_the_emulators_own_command_line(self):
        self.assertFalse(emulator.runs_avd(os.getpid(), "sello_x"))
        with tempfile.NamedTemporaryFile() as fake:
            fake.write(b"/sdk/emulator/emulator\0-port\0005554\0-avd\0sello_x\0-no-window\0")
            fake.flush()
            real_path = Path

            def proc(path):
                return real_path(fake.name) if str(path).startswith("/proc/") else real_path(path)

            with mock.patch.object(emulator, "Path", proc):
                self.assertTrue(emulator.runs_avd(1, "sello_x"))
                self.assertFalse(emulator.runs_avd(1, "sello_y"))


if __name__ == "__main__":
    unittest.main()
