import io
import os
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import evidence
import runner
from support import epic, git, make_repo, ticket, write, write_epic
from test_runner import DEVICE, Harness


class MergeCandidateTests(unittest.TestCase):
    def setUp(self):
        device_env = patch.dict(os.environ, {"ANDROID_SERIAL": ""})
        device_env.start()
        self.addCleanup(device_env.stop)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = make_repo(directory.name, epic(
            ticket("SELLO-003", status="Done"),
            ticket("SELLO-004", status="Backlog", dependencies="SELLO-003"),
            ticket("SELLO-005", status="Backlog", dependencies="SELLO-004", gate="G2"),
        ))
        self.base = git(self.root, "rev-parse", "HEAD").strip()
        self.harness = Harness(self.root)
        self.devices = [DEVICE]

    def set_statuses(self, four, five="Backlog"):
        write_epic(self.root, epic(
            ticket("SELLO-003", status="Done"),
            ticket("SELLO-004", status=four, dependencies="SELLO-003"),
            ticket("SELLO-005", status=five, dependencies="SELLO-004", gate="G2"),
        ))
        git(self.root, "add", "-A")
        git(self.root, "commit", "-qm", "statuses")

    def run_candidate(self, base=None, gate=None):
        self.stream = io.StringIO()
        return runner.run_merge_candidate(
            base or self.base, self.root, requested_gate=gate, execute=self.harness.execute,
            environment=lambda root: {}, devices=lambda: self.devices, stream=self.stream,
        )

    def reports(self, paths):
        return [json.loads(Path(path).read_text(encoding="utf-8")) for path in paths]

    def test_no_active_ticket_selects_nothing(self):
        self.assertEqual([], runner.merge_candidate_tickets(self.root, self.base))

    def test_in_progress_and_review_tickets_are_selected(self):
        self.set_statuses("In Progress")
        self.assertEqual(["SELLO-004"], runner.merge_candidate_tickets(self.root, self.base))
        self.set_statuses("Review")
        self.assertEqual(["SELLO-004"], runner.merge_candidate_tickets(self.root, self.base))

    def test_ticket_completed_since_the_base_is_selected(self):
        self.set_statuses("Done")
        self.assertEqual(["SELLO-004"], runner.merge_candidate_tickets(self.root, self.base))

    def test_ticket_already_done_at_the_base_is_not_selected(self):
        self.set_statuses("Done")
        head = git(self.root, "rev-parse", "HEAD").strip()
        self.assertEqual([], runner.merge_candidate_tickets(self.root, head))

    def test_unknown_base_is_refused(self):
        code, paths = self.run_candidate(base="0" * 40)
        self.assertNotEqual(0, code)
        self.assertEqual([], paths)
        self.assertEqual([], self.harness.executed)
        self.assertIn("Unknown base revision", self.stream.getvalue())

    def test_active_ticket_is_verified_at_its_local_gate(self):
        self.set_statuses("Done", "In Progress")
        code, paths = self.run_candidate()
        reports = self.reports(paths)
        self.assertEqual(0, code)
        self.assertEqual(["SELLO-004", "SELLO-005"], [report["ticket"] for report in reports])
        self.assertEqual(["G1", "G2"], [report["gate"]["required"] for report in reports])
        self.assertEqual(["G1", "G1"], [report["gate"]["executed"] for report in reports])
        self.assertNotIn("gradle-device", self.harness.executed)

    def test_requested_gate_applies_to_every_ticket(self):
        self.set_statuses("In Progress")
        code, paths = self.run_candidate(gate="G2")
        self.assertEqual(0, code)
        self.assertEqual({"required": "G1", "executed": "G2"}, self.reports(paths)[0]["gate"])

    def test_one_failing_ticket_fails_the_candidate(self):
        self.set_statuses("Done", "In Progress")

        def fail_the_second_host_gate(name):
            if name == "gradle-host" and self.harness.executed.count("gradle-host") == 2:
                self.harness.exit_codes["gradle-host"] = 1
        self.harness.during = fail_the_second_host_gate
        code, paths = self.run_candidate()
        self.assertNotEqual(0, code)
        self.assertEqual(["passed", "failed"], [report["outcome"] for report in self.reports(paths)])

    def test_unticketed_change_still_runs_the_gate(self):
        code, paths = self.run_candidate()
        report = self.reports(paths)[0]
        self.assertEqual(0, code)
        self.assertEqual("UNTICKETED", report["ticket"])
        self.assertEqual({"required": "G0", "executed": "G0"}, report["gate"])
        self.assertNotIn("gradle-host", self.harness.executed)
        self.assertIsNone(evidence.schema_problem(report, evidence.SCHEMA))

    def test_unticketed_failure_fails_the_candidate(self):
        self.harness.exit_codes["architecture"] = 1
        self.assertNotEqual(0, self.run_candidate()[0])

    def test_unticketed_report_cannot_become_ticket_evidence(self):
        code, paths = self.run_candidate()
        with self.assertRaisesRegex(evidence.EvidenceError, "not tied to a ticket"):
            evidence.retain(self.root, paths[0])

    def test_candidate_needing_a_device_is_refused_without_one(self):
        self.devices = []
        code, paths = self.run_candidate(gate="G2")
        self.assertNotEqual(0, code)
        self.assertEqual([], self.harness.executed)


if __name__ == "__main__":
    unittest.main()
