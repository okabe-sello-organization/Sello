"""Ticket files: docs/tickets/<epic>/SELLO-0NN.md is the canonical ticket."""
import io
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path

import tickets

SECTIONS = """### Outcome
A complete, independently verifiable task.

### Acceptance criteria
- The existing project keeps working.
"""

def epic_readme(identifier="SELLO-E01", status="Backlog", increment=None):
    extra = f"- **Increment:** {increment}\n" if increment else ""
    return (f"# {identifier} — Foundation\n\n- **Status:** {status}\n{extra}"
            "- **Goal:** Build a trustworthy foundation.\n- **Exit:** Working app and verified contracts.\n")


def ticket_text(identifier="SELLO-001", status="Backlog", dependencies="None", gate="G1"):
    return (f"# {identifier} — Complete a working task\n\n- **Type:** Task\n- **Priority:** P0\n"
            f"- **Status:** {status}\n- **Depends on:** {dependencies}\n- **Gate:** {gate}\n\n{SECTIONS}")


class TicketFileTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def epic(self, folder="E01-foundation", readme=None, **ticket_files):
        self.write(f"docs/tickets/{folder}/README.md", readme or epic_readme())
        for identifier, text in ticket_files.items():
            self.write(f"docs/tickets/{folder}/{identifier.replace('_', '-')}.md", text)

    def load(self):
        return tickets.load(self.root)

    def validate(self):
        tickets.validate(self.load())

    def test_parses_a_complete_ticket(self):
        self.epic(SELLO_001=ticket_text(status="Ready"))
        epics = self.load()
        tickets.validate(epics)
        only = epics[0].tickets[0]
        self.assertEqual(("SELLO-001", "Complete a working task", (), "G1", "SELLO-E01"),
                         (only.identifier, only.title, only.dependencies, only.gate, only.epic_id))

    def test_file_name_must_match_the_ticket_heading(self):
        self.epic(SELLO_002=ticket_text("SELLO-001"))
        with self.assertRaisesRegex(ValueError, "file SELLO-002.md holds SELLO-001"):
            self.load()

    def test_a_ticket_folder_is_rejected(self):
        self.epic(SELLO_001=ticket_text())
        self.write("docs/tickets/E01-foundation/SELLO-002/ticket.md", ticket_text("SELLO-002"))
        with self.assertRaisesRegex(ValueError, "ticket folders SELLO-002; a ticket is one file"):
            self.load()

    def test_old_sections_and_images_are_allowed(self):
        self.epic(SELLO_001=ticket_text() + "\n### Implementation plan\n1. Build it.\n")
        self.write("docs/tickets/E01-foundation/attachments/SELLO-001-screen.png", "png")
        self.assertEqual(["SELLO-001"], [t.identifier for t in self.load()[0].tickets])

    def test_epic_folder_without_a_readme_is_rejected(self):
        self.write("docs/tickets/E01-foundation/SELLO-001.md", ticket_text())
        with self.assertRaisesRegex(ValueError, "README.md"):
            self.load()

    def test_stray_files_and_folders_are_rejected(self):
        for stray in ("docs/tickets/E01-foundation/notes.md", "docs/tickets/E01-foundation/SELLO-12.md",
                      "docs/tickets/E01-foundation/images/a.png", "docs/tickets/SELLO-002.md",
                      "docs/tickets/E23_misnamed/SELLO-003.md"):
            with self.subTest(stray=stray):
                directory = tempfile.TemporaryDirectory()
                self.addCleanup(directory.cleanup)
                self.root = Path(directory.name)
                self.epic(SELLO_001=ticket_text())
                self.write(stray, ticket_text("SELLO-003"))
                with self.assertRaisesRegex(ValueError, "epic folder"):
                    self.load()

    def test_rejects_invalid_fields_and_duplicate_metadata(self):
        for old, new, message in (("- **Type:** Task", "- **Type:** Epic", "invalid type/gate"),
                                  ("- **Gate:** G1", "- **Gate:** G9", "invalid type/gate"),
                                  ("- **Priority:** P0", "- **Priority:** P9", "invalid status/priority"),
                                  ("- **Status:** Backlog", "- **Status:** Started", "invalid status/priority"),
                                  ("- **Gate:** G1", "- **Gate:** G1\n- **Gate:** G2", "Duplicate metadata")):
            with self.subTest(new=new):
                self.epic(SELLO_001=ticket_text().replace(old, new))
                with self.assertRaisesRegex(ValueError, message):
                    self.validate()

    def test_template_folder_is_not_an_epic(self):
        self.epic(SELLO_001=ticket_text())
        self.write("docs/tickets/TEMPLATE/README.md", "# Template\n")
        self.write("docs/tickets/TEMPLATE/SELLO-0NN.md", "# SELLO-0NN — Title\n")
        self.assertEqual(["SELLO-E01"], [epic.identifier for epic in self.load()])

    def test_preserves_post_mvp_increment_and_rejects_unknown(self):
        self.epic(readme=epic_readme(increment="Post-MVP"), SELLO_001=ticket_text())
        self.assertEqual("Post-MVP", self.load()[0].increment)
        self.epic(readme=epic_readme(increment="Unknown"), SELLO_001=ticket_text())
        with self.assertRaisesRegex(ValueError, "invalid increment"):
            self.validate()

    def test_new_epic_requires_explicit_increment(self):
        self.epic("E09-carryover", readme=epic_readme("SELLO-E09"), SELLO_001=ticket_text())
        with self.assertRaisesRegex(ValueError, "missing Increment"):
            self.load()

    def test_mvp_cannot_depend_on_post_mvp(self):
        self.epic(SELLO_001=ticket_text(dependencies="SELLO-002"))
        self.epic("E09-carryover", readme=epic_readme("SELLO-E09", increment="Post-MVP"), SELLO_002=ticket_text("SELLO-002"))
        with self.assertRaisesRegex(ValueError, "MVP depends on Post-MVP"):
            self.validate()

    def test_rejects_missing_required_sections(self):
        for section in ("Outcome", "Acceptance criteria"):
            self.epic(SELLO_001=ticket_text().replace(f"### {section}", "### Other"))
            with self.assertRaisesRegex(ValueError, section):
                self.load()

    def test_rejects_empty_outcome(self):
        self.epic(SELLO_001=ticket_text().replace("A complete, independently verifiable task.", ""))
        with self.assertRaisesRegex(ValueError, "Outcome"):
            self.load()

    def test_rejects_duplicate_ids_across_epics(self):
        self.epic(SELLO_001=ticket_text())
        self.epic("E02-design", readme=epic_readme("SELLO-E02"), SELLO_001=ticket_text())
        with self.assertRaisesRegex(ValueError, "Duplicate ticket"):
            self.validate()

    def test_rejects_missing_dependency_and_cycles(self):
        self.epic(SELLO_001=ticket_text(dependencies="SELLO-999"))
        with self.assertRaisesRegex(ValueError, "Unknown dependency"):
            self.validate()
        self.epic(SELLO_001=ticket_text(dependencies="SELLO-002"), SELLO_002=ticket_text("SELLO-002", dependencies="SELLO-001"))
        with self.assertRaisesRegex(ValueError, "cycle"):
            self.validate()

    def test_ready_requires_completed_dependencies(self):
        self.epic(SELLO_001=ticket_text(), SELLO_002=ticket_text("SELLO-002", status="Ready", dependencies="SELLO-001"))
        with self.assertRaisesRegex(ValueError, "unfinished dependencies"):
            self.validate()

    def test_no_ticket_needs_a_quality_dependency(self):
        self.epic(SELLO_005=ticket_text("SELLO-005"))
        self.validate()

    def test_done_needs_no_evidence_or_handoff_file(self):
        self.epic(SELLO_001=ticket_text(status="Done"), SELLO_002=ticket_text("SELLO-002", status="Done", dependencies="SELLO-001"))
        self.validate()

    def test_done_requires_done_dependencies(self):
        self.epic(SELLO_001=ticket_text(), SELLO_002=ticket_text("SELLO-002", status="Done", dependencies="SELLO-001"))
        with self.assertRaisesRegex(ValueError, "unfinished dependencies for Done"):
            self.validate()

    def test_epic_cannot_be_done_with_unfinished_children(self):
        self.epic(readme=epic_readme(status="Done"), SELLO_001=ticket_text())
        with self.assertRaisesRegex(ValueError, "unfinished children"):
            self.validate()

    def test_status_table_is_printed_on_demand_not_committed(self):
        self.epic(SELLO_001=ticket_text(status="Done"), SELLO_002=ticket_text("SELLO-002", dependencies="SELLO-001"))
        table = tickets.status_table(self.load())
        self.assertIn("| SELLO-001 |", table)
        self.assertIn("Done: 1", table)
        self.assertIn("SELLO-002", table)
        self.assertEqual([], list((self.root / "docs/tickets").glob("*.md")))

    def test_cli_check_and_status(self):
        self.epic(SELLO_001=ticket_text())
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(0, tickets.main(["check"], root=self.root))
            self.assertEqual(0, tickets.main(["status", "E01"], root=self.root))
        self.assertIn("PASS: 1 epics, 1 tickets", out.getvalue())
        self.assertIn("SELLO-001", out.getvalue())
        self.epic(SELLO_001=ticket_text(status="Ready", dependencies="SELLO-999"))
        with redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(1, tickets.main(["check"], root=self.root))
        self.assertIn("Unknown dependency", err.getvalue())



if __name__ == "__main__":
    unittest.main()
