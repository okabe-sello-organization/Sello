"""The documented gates must be the gates scripts/check runs."""
import re
import unittest
from pathlib import Path
from unittest.mock import patch

import gates

GUIDE = Path(__file__).resolve().parents[2] / "docs/development/quality-gates.md"


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
                     ":app:testDebugUnitTest", ":catalog:testDebugUnitTest", "lintDebug",
                     ":app:assembleRelease", ":app:assembleDebug", ":catalog:assembleDebug"):
            self.assertIn(task, gates.HOST_TASKS)
        self.assertEqual(("ktlintCheck",), gates.FORMAT_TASKS)
        self.assertEqual(["tickets", "quality-tests", "performance-tests", "architecture", "links"],
                         [name for name, _ in gates.FAST_CHECKS])

    def test_ci_release_checks_sign_with_a_throwaway_key_and_never_claim_production(self):
        self.assertEqual(["release-licenses", "release-ephemeral"], [name for name, _ in gates.release_checks(ci=True)])
        local = gates.release_checks(ci=False)
        self.assertEqual(["release-licenses", "release-production", "release-launch"], [name for name, _ in local])
        self.assertIn("production", local[1][1])

    def test_the_app_suite_runs_as_two_shards_and_the_rest_as_one_group(self):
        self.assertEqual(["app-1", "app-2", "support"], list(gates.DEVICE_GROUPS))
        gates.validate_device_groups()
        self.assertEqual(("./gradlew", ":app:connectedDebugAndroidTest",
                          "-Pandroid.testInstrumentationRunnerArguments.numShards=2",
                          "-Pandroid.testInstrumentationRunnerArguments.shardIndex=1", "--continue"),
                         gates.device_tests(("app",), gates.DEVICE_GROUPS["app-2"].shard))
        self.assertNotIn("numShards", " ".join(gates.device_tests(("data", "catalog"))))
        self.assertEqual(("./gradlew", "ktlintCheck", ":data:assembleDebugAndroidTest", "--continue"),
                         gates.device_build(("data",)))

    def test_an_incomplete_or_inconsistent_shard_split_is_refused(self):
        support = gates.DEVICE_GROUPS["support"]
        for name, groups in (
            ("missing shard", {"app-1": gates.Group(("app",), (0, 2)), "support": support}),
            ("same shard twice", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (0, 2)), "support": support}),
            ("different counts", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (1, 3)), "support": support}),
            ("whole run and a shard", {"app-1": gates.Group(("app",)), "app-2": gates.Group(("app",), (1, 2)), "support": support}),
            ("selection map disagrees", {"app-1": gates.Group(("app",), (0, 2)), "app-2": gates.Group(("app",), (1, 2)),
                                         "support": gates.Group(("data", "design-system"))}),
        ):
            with self.subTest(name=name), patch.dict(gates.DEVICE_GROUPS, groups, clear=True):
                with self.assertRaises(ValueError):
                    gates.validate_device_groups()

    def test_unit_test_tasks(self):
        self.assertEqual(":domain:test", gates.unit_test_task("domain"))
        self.assertEqual(":app:testDebugUnitTest", gates.unit_test_task("app"))


if __name__ == "__main__":
    unittest.main()
