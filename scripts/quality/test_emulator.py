"""scripts/emulator: argument handling, the port it picks and what it refuses to stop."""
import contextlib
import io
import os
import tempfile
import unittest
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


class ConfigTest(unittest.TestCase):
    def test_appends_what_the_ci_action_appends(self):
        path, lines = emulator.avd_config("/avds", "sello_1")
        self.assertEqual("/avds/sello_1.avd/config.ini", str(path))
        self.assertEqual("hw.cpu.ncore=2\n", lines)

    def test_matches_the_workflow(self):
        with open(os.path.join(os.path.dirname(__file__), "../../.github/workflows/quality.yml"),
                  encoding="utf-8") as workflow:
            text = workflow.read()
        self.assertIn(f"api-level: {emulator.API_LEVEL}\n", text)
        self.assertIn(f"target: {emulator.TARGET}\n", text)
        self.assertIn(f"arch: {emulator.ARCH}\n", text)
        self.assertIn(f"cores: {emulator.CORES}\n", text)
        self.assertIn(f"emulator-options: {' '.join(emulator.EMULATOR_OPTIONS)}\n", text)
        self.assertIn("disable-animations: true\n", text)
        self.assertNotIn("profile:", text)


class StopTest(unittest.TestCase):
    def test_leaves_an_emulator_it_did_not_start_alone(self):
        with tempfile.TemporaryDirectory() as cache, \
                mock.patch.dict(os.environ, {"XDG_CACHE_HOME": cache}), \
                mock.patch.object(emulator, "adb") as adb, \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(1, emulator.main(["stop", "emulator-5554"], out=io.StringIO()))
        adb.assert_not_called()
        self.assertIn("was not started by scripts/emulator", err.getvalue())


if __name__ == "__main__":
    unittest.main()
