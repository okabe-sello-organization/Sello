import io
import os
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import evidence
import runner
from support import epic, evidence_file, git, make_repo, ticket, ticket_file, write, write_epic
from test_runner import DEVICE, Harness


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        device_env = patch.dict(os.environ, {"ANDROID_SERIAL": ""})
        device_env.start()
        self.addCleanup(device_env.stop)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = make_repo(directory.name)
        self.harness = Harness(self.root)
        self.field = self.verify_and_retain()

    def verify(self, identifier="SELLO-004", gate=None):
        code, path = runner.run(
            identifier, self.root, requested_gate=gate, execute=self.harness.execute,
            environment=lambda root: {}, devices=lambda: [DEVICE], stream=io.StringIO(),
        )
        return code, path

    def verify_and_retain(self):
        code, path = self.verify()
        self.assertEqual(0, code)
        return evidence.retain(self.root, path)

    def check(self, identifier="SELLO-004", gate="G1", field=None, fresh=True):
        evidence.check(self.root, identifier, gate, self.field if field is None else field, fresh)

    def retained(self):
        return evidence.retained_path(self.root, "SELLO-004")

    def rewrite(self, change):
        report = json.loads(self.retained().read_text(encoding="utf-8"))
        change(report)
        self.retained().write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        try:
            return evidence.quality_run_line(self.root, "SELLO-004")
        except evidence.EvidenceError:
            return self.field  # malformed on purpose; the check fails before the field is read

    def assert_rejected(self, message, **arguments):
        with self.assertRaisesRegex(evidence.EvidenceError, message):
            self.check(**arguments)

    def test_fresh_passing_report_is_accepted(self):
        self.check()

    def test_quality_run_line_identifies_the_run(self):
        report = json.loads(self.retained().read_text(encoding="utf-8"))
        self.assertIn(report["run_id"], self.field)
        self.assertIn("SELLO-004 G1 passed", self.field)
        self.assertIn("docs/tickets/E01-foundation/SELLO-004/quality-report.json", self.field)

    def test_missing_report_is_rejected(self):
        self.retained().unlink()
        self.assert_rejected("no retained quality report")

    def test_unparseable_report_is_rejected(self):
        self.retained().write_text("{not json", encoding="utf-8")
        self.assert_rejected("not valid JSON")

    def test_report_missing_a_required_field_is_rejected(self):
        field = self.rewrite(lambda report: report.pop("checks"))
        self.assert_rejected("malformed report: checks is required", field=field)

    def test_report_with_wrong_type_is_rejected(self):
        field = self.rewrite(lambda report: report["snapshot"].update(dirty="no"))
        self.assert_rejected("malformed report: snapshot.dirty", field=field)

    def test_unsupported_schema_version_is_rejected(self):
        field = self.rewrite(lambda report: report.update(schema_version=999))
        self.assert_rejected("malformed report: schema_version", field=field)

    def test_failed_report_is_rejected(self):
        field = self.rewrite(lambda report: report.update(outcome="failed"))
        self.assert_rejected("outcome is failed", field=field)

    def test_failed_run_cannot_be_retained(self):
        self.harness.exit_codes["quality-tests"] = 1
        code, path = self.verify()
        self.assertNotEqual(0, code)
        with self.assertRaisesRegex(evidence.EvidenceError, "only a passed run"):
            evidence.retain(self.root, path)

    def test_passed_report_with_a_failed_check_is_rejected(self):
        field = self.rewrite(lambda report: report["checks"][0].update(exit_code=1))
        self.assert_rejected("check tickets-check exited 1", field=field)

    def test_report_for_another_ticket_is_rejected(self):
        write_epic(self.root, epic(
            ticket("SELLO-003", status="Done"),
            ticket("SELLO-004", status="Done", dependencies="SELLO-003"),
            ticket("SELLO-005", status="Review", dependencies="SELLO-004"),
        ))
        other = evidence.retained_path(self.root, "SELLO-005")
        other.write_bytes(self.retained().read_bytes())
        field = evidence.quality_run_line(self.root, "SELLO-005")
        self.assert_rejected("report is for SELLO-004", identifier="SELLO-005", field=field)

    def test_host_gate_report_completes_a_device_ticket(self):
        # The device suites of a G2 ticket run hosted, on the merge result (ADR 0009/0014).
        self.check(gate="G2")

    def test_host_gate_report_does_not_complete_a_release_ticket(self):
        self.assert_rejected("G1 does not satisfy G3", gate="G3")

    def test_higher_gate_satisfies_a_lower_one(self):
        code, path = self.verify(gate="G2")
        self.assertEqual(0, code)
        self.check(field=evidence.retain(self.root, path))

    def release_evidence(self):
        write_epic(self.root, epic(
            ticket("SELLO-003", status="Done"),
            ticket("SELLO-004", status="In Progress", dependencies="SELLO-003", gate="G3"),
        ))
        git(self.root, "add", "-A")
        git(self.root, "commit", "-q", "-m", "release ticket")
        code, path = self.verify()
        self.assertEqual(0, code)
        return evidence.retain(self.root, path)

    def test_a_release_report_with_the_owners_signing_completes_a_release_ticket(self):
        field = self.release_evidence()
        self.check(gate="G3", field=field)

    def test_a_release_report_without_the_owners_signing_cannot_complete_a_release_ticket(self):
        self.release_evidence()
        field = self.rewrite(lambda report: report.update(
            checks=[check for check in report["checks"] if check["name"] != "release-production"]))
        self.assert_rejected("owner's production signing", gate="G3", field=field)

    def test_code_change_after_the_run_is_stale(self):
        write(self.root, "app/src/Main.kt", "fun main() = println(3)\n")
        self.assert_rejected("stale")

    def test_untracked_source_after_the_run_is_stale(self):
        write(self.root, "app/src/Later.kt", "class Later\n")
        self.assert_rejected("stale")

    def test_unrelated_change_on_main_keeps_the_report_fresh(self):
        git(self.root, "checkout", "-q", "-b", "ticket")
        write(self.root, "app/src/Feature.kt", "class Feature\n")
        git(self.root, "add", "-A")
        git(self.root, "commit", "-qm", "feature")
        self.field = self.verify_and_retain()
        git(self.root, "add", "-A")
        git(self.root, "commit", "-qm", "evidence")
        git(self.root, "checkout", "-q", "main")
        write(self.root, "app/src/Other.kt", "class Other\n")
        git(self.root, "add", "-A")
        git(self.root, "commit", "-qm", "another ticket")
        git(self.root, "checkout", "-q", "ticket")
        git(self.root, "merge", "-q", "main")
        self.check()
        write(self.root, "app/src/Feature.kt", "class Feature { val x = 1 }\n")
        self.assert_rejected("stale")

    def test_report_without_change_binding_cannot_complete_a_ticket(self):
        field = self.rewrite(lambda report: report["snapshot"].pop("changes"))
        self.assert_rejected("change", field=field)
        self.check(field=field, fresh=False)

    def test_historical_report_survives_later_unrelated_changes(self):
        write(self.root, "app/src/Main.kt", "fun main() = println(3)\n")
        self.check(fresh=False)

    def test_acceptance_edit_invalidates_even_historical_evidence(self):
        path = self.root / ticket_file("SELLO-004")
        write(self.root, ticket_file("SELLO-004"), path.read_text(encoding="utf-8").replace("keeps working", "mostly works"))
        self.assert_rejected("acceptance text changed", fresh=False)

    def test_another_tickets_acceptance_edit_keeps_historical_evidence(self):
        path = self.root / ticket_file("SELLO-003")
        write(self.root, ticket_file("SELLO-003"), path.read_text(encoding="utf-8").replace("keeps working", "mostly works"))
        self.check(fresh=False)

    def test_status_and_evidence_edits_do_not_stale_the_report(self):
        path = self.root / ticket_file("SELLO-004")
        text = path.read_text(encoding="utf-8").replace("In Progress\n- **Depends", "Review\n- **Depends")
        write(self.root, ticket_file("SELLO-004"), text + "\n### Execution progress\n\n- Verified.\n")
        write(self.root, evidence_file("SELLO-004"), f"# SELLO-004\n\n- **Quality run:** {self.field}\n")
        write(self.root, "docs/tickets/E01-foundation/SELLO-004/handoff.md", "# SELLO-004\n\nDone.\n")
        write(self.root, "docs/tickets/E01-foundation/SELLO-004/artifacts/walk.png", "png")
        write(self.root, "docs/development/HANDOFF.md", "informative\n")
        self.check()

    def test_field_naming_a_different_run_is_rejected(self):
        self.assert_rejected("does not name run", field="run 20260101T000000Z-00000000, passed")

    def test_report_edited_after_being_recorded_is_rejected(self):
        self.rewrite(lambda report: report["warnings"].append("edited"))
        self.assert_rejected("does not match the recorded sha256")

    def test_status_at_reads_a_ticket_status_from_a_commit(self):
        self.assertEqual("In Progress", evidence.status_at(self.root, "HEAD", "SELLO-004"))
        self.assertEqual("Done", evidence.status_at(self.root, "HEAD", "SELLO-003"))
        self.assertIsNone(evidence.status_at(self.root, "HEAD", "SELLO-777"))
        self.assertIsNone(evidence.status_at(self.root, "no-such-ref", "SELLO-004"))


if __name__ == "__main__":
    unittest.main()
