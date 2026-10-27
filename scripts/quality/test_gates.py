"""The documented gate must be the gate the runner executes."""
import re
import unittest
from unittest.mock import patch
from pathlib import Path

import gates

README = Path(__file__).resolve().parents[2] / "docs/tickets/README.md"


class GateDocumentationTests(unittest.TestCase):
    def setUp(self):
        self.text = README.read_text(encoding="utf-8")

    def test_documented_host_command_matches_the_runner(self):
        block = re.search(r"```bash\n(\./gradlew ktlintCheck[\s\S]*?)```", self.text).group(1)
        documented = block.replace("\\\n", " ").split()
        self.assertEqual(["./gradlew", *gates.HOST_TASKS, "--continue"], documented)

    def test_documented_device_tasks_match_the_runner(self):
        for task in gates.DEVICE_TASKS:
            self.assertIn(f"`{task}`", self.text)

    def test_host_gate_runs_every_module_and_both_static_checks(self):
        names = [check.name for check in gates.plan("G1")]
        self.assertEqual(["tickets-check", "quality-tests", "performance-tests", "architecture", "links", "gradle-host"], names)
        self.assertIn("ktlintCheck", gates.HOST_TASKS)
        self.assertIn("lintDebug", gates.HOST_TASKS)

    def test_device_gate_extends_the_host_gate(self):
        host, device = gates.plan("G1"), gates.plan("G2")
        self.assertEqual(host, device[: len(host)])
        self.assertTrue(device[-1].needs_device)

    def test_release_gate_extends_the_device_gate_with_the_owners_signing(self):
        device, release = gates.plan("G2"), gates.plan("G3")
        self.assertEqual(device, release[: len(device)])
        self.assertEqual(["release-licenses", "release-production", "release-launch"],
                         [check.name for check in release[len(device):]])
        self.assertIn("production", release[-2].argv)
        self.assertTrue(release[-1].needs_device)

    def test_ci_release_gate_signs_with_a_throwaway_key_and_never_claims_production(self):
        device, release = gates.plan("G2"), gates.plan("G3", ci=True)
        self.assertEqual(device, release[: len(device)])
        self.assertEqual(["release-licenses", "release-ephemeral"], [check.name for check in release[len(device):]])
        self.assertNotIn("release-production", [check.name for check in release])

    def test_lower_gates_are_the_same_on_ci(self):
        for gate in ("G0", "G1", "G2"):
            self.assertEqual(gates.plan(gate), gates.plan(gate, ci=True))


if __name__ == "__main__":
    unittest.main()

    def test_the_app_suite_runs_as_two_shards_and_the_rest_as_one_group(self):
        self.assertEqual(['app-1', 'app-2', 'support'], list(gates.DEVICE_GROUPS))
        self.assertEqual((0, 2), gates.DEVICE_GROUPS['app-1'].shard)
        self.assertEqual((1, 2), gates.DEVICE_GROUPS['app-2'].shard)
        self.assertEqual((), gates.DEVICE_GROUPS['support'].shard)
        gates.validate_device_groups()
        check = gates.device_check('gradle-device-app-2', gates.DEVICE_GROUPS['app-2'].tasks, gates.DEVICE_GROUPS['app-2'].shard)
        self.assertEqual(('./gradlew', ':app:connectedDebugAndroidTest',
                          '-Pandroid.testInstrumentationRunnerArguments.numShards=2',
                          '-Pandroid.testInstrumentationRunnerArguments.shardIndex=1', '--continue'), check.argv)
        self.assertEqual((1, 2), check.shard)
        self.assertEqual(('app',), check.result_modules)
        self.assertNotIn('numShards', ' '.join(gates.device_check().argv))

    def test_an_incomplete_or_inconsistent_shard_split_is_refused(self):
        app = ':app:connectedDebugAndroidTest'
        support = gates.DEVICE_GROUPS['support']
        for name, groups in (
            ('missing shard', {'app-1': gates.Group((app,), (0, 2)), 'support': support}),
            ('same shard twice', {'app-1': gates.Group((app,), (0, 2)), 'app-2': gates.Group((app,), (0, 2)), 'support': support}),
            ('different counts', {'app-1': gates.Group((app,), (0, 2)), 'app-2': gates.Group((app,), (1, 3)), 'support': support}),
            ('whole run and a shard', {'app-1': gates.Group((app,)), 'app-2': gates.Group((app,), (1, 2)), 'support': support}),
            ('selection map disagrees', {'app-1': gates.Group((app,), (0, 2)), 'app-2': gates.Group((app,), (1, 2)),
                                         'support': gates.Group((':data:connectedDebugAndroidTest',))}),
        ):
            with self.subTest(name=name), patch.dict(gates.DEVICE_GROUPS, groups, clear=True):
                with self.assertRaises(gates.Unavailable):
                    gates.validate_device_groups()

