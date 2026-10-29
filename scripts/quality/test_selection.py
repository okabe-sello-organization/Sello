"""The plan: documents get G0, tooling gets the host gate, code selects the device suites it can affect."""
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

import selection

ALL = ["data", "design-system", "app", "catalog"]


def pr(*paths):
    return selection.plan("pull_request", paths)


class PullRequestPlanTests(unittest.TestCase):
    def test_documents_alone_get_g0_even_when_they_advance_a_ticket(self):
        for paths in (("docs/tickets/E09-carryover/SELLO-040/ticket.md",),
                      ("docs/tickets/E09-carryover/SELLO-040/ticket.md", "docs/tickets/E09-carryover/README.md",
                       "docs/tickets/E09-carryover/SELLO-040/artifacts/after.png"),
                      ("README.md", "AGENTS.md", ".github/pull_request_template.md", "prototype/README.md"),
                      ("docs/release/dependencies.md", "docs/design/v2/screens.html"), ()):
            with self.subTest(paths=paths):
                plan = pr(*paths)
                self.assertEqual(("G0", False, [], []), (plan["gate"], plan["host"], plan["modules"], plan["groups"]))

    def test_python_tooling_gets_the_host_gate_without_device_suites(self):
        for path in ("scripts/quality/tickets.py", "scripts/quality/links.py", "scripts/quality/test_tickets.py",
                     "scripts/quality/release.py", "scripts/performance/measure.py", "scripts/update-dependency-locks"):
            with self.subTest(path=path):
                plan = pr(path, "docs/notes.md")
                self.assertEqual(("G1", True, [], []), (plan["gate"], plan["host"], plan["modules"], plan["groups"]))

    def test_scripts_the_device_jobs_run_select_every_device_group(self):
        for path in selection.DEVICE_SCRIPTS:
            with self.subTest(path=path):
                plan = pr(path)
                self.assertEqual(("G2", ALL, ["app-1", "app-2", "support"]), (plan["gate"], plan["modules"], plan["groups"]))

    def test_each_module_selects_the_suites_of_its_consumers(self):
        expected = {
            "app/src/main/java/A.kt": (["app"], ["app-1", "app-2"]),
            "catalog/src/main/java/A.kt": (["catalog"], ["support"]),
            "data/src/main/java/A.kt": (["data", "app"], ["app-1", "app-2", "support"]),
            "domain/src/main/kotlin/A.kt": (["data", "app"], ["app-1", "app-2", "support"]),
            "design-system/src/main/java/A.kt": (["design-system", "app", "catalog"], ["app-1", "app-2", "support"]),
            "data/schemas/com.software.sello.data.local.SelloDatabase/9.json": (["data", "app"], ["app-1", "app-2", "support"]),
            "app/src/debug/java/Sandbox.kt": (["app"], ["app-1", "app-2"]),
            "app/src/androidTestDebug/java/AppTest.kt": (["app"], ["app-1", "app-2"]),
            "data/src/androidTest/java/DaoTest.kt": (["data"], ["support"]),
            "design-system/src/androidTest/java/ButtonTest.kt": (["design-system"], ["support"]),
        }
        for path, (modules, groups) in expected.items():
            with self.subTest(path=path):
                plan = pr(path)
                self.assertEqual(("G2", modules, groups), (plan["gate"], plan["modules"], plan["groups"]))

    def test_unit_tests_alone_need_only_the_host_gate(self):
        plan = pr("domain/src/test/kotlin/MoneyTest.kt", "app/src/testDebug/java/ScenarioTest.kt", "data/src/test/java/A.kt")
        self.assertEqual(("G1", []), (plan["gate"], plan["modules"]))

    def test_mixed_changes_union_their_suites(self):
        self.assertEqual(["data", "app", "catalog"], pr("data/src/main/A.kt", "catalog/src/main/B.kt", "docs/x.md")["modules"])

    def test_build_files_workflows_lockfiles_gradle_and_unknown_paths_select_everything(self):
        for path in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle/libs.versions.toml",
                     "gradle/wrapper/gradle-wrapper.properties", "app/build.gradle.kts", "data/gradle.lockfile",
                     "settings-gradle.lockfile", "app/proguard-rules.pro", ".github/workflows/quality.yml",
                     ".editorconfig", "gradlew", "prototype/src/main/A.kt", "new-module/src/main/A.kt",
                     "docs/check.sh", "app/lint.xml"):
            with self.subTest(path=path):
                plan = pr(path)
                self.assertEqual(("G2", ALL, ["app-1", "app-2", "support"]), (plan["gate"], plan["modules"], plan["groups"]))

    def test_the_plan_names_what_each_path_selects(self):
        plan = pr("docs/a.md", "scripts/quality/links.py", "app/src/main/A.kt")
        self.assertEqual({"docs/a.md": "document", "scripts/quality/links.py": "host", "app/src/main/A.kt": "app"}, plan["changed"])
        self.assertIn("app-1, app-2", selection.summary(plan))


class EventPlanTests(unittest.TestCase):
    def test_a_push_to_main_keeps_the_host_gate_and_documents_get_g0(self):
        self.assertEqual(("G1", []), tuple(selection.plan("push", ["app/src/main/A.kt"])[k] for k in ("gate", "groups")))
        self.assertEqual("G0", selection.plan("push", ["docs/a.md"])["gate"])

    def test_nightly_and_manual_runs_verify_everything_at_g3(self):
        for event in ("schedule", "workflow_dispatch"):
            for paths in ([], ["docs/a.md"], ["app/src/main/A.kt"]):
                with self.subTest(event=event, paths=paths):
                    plan = selection.plan(event, paths)
                    self.assertEqual(("G3", True, True, ALL, ["app-1", "app-2", "support"]),
                                     (plan["gate"], plan["host"], plan["release"], plan["modules"], plan["groups"]))

    def test_an_unknown_event_is_refused(self):
        with self.assertRaises(ValueError):
            selection.plan("merge_group", [])


class RepositoryPlanTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        self.write("app/src/main/Main.kt", "fun main() = Unit\n")
        self.write("docs/notes.md", "Notes.\n")
        self.git("add", "-A")
        self.git("commit", "-qm", "initial")
        self.base = self.git("rev-parse", "HEAD").strip()

    def git(self, *arguments):
        return subprocess.run(["git", "-C", str(self.root), *arguments], check=True, capture_output=True, text=True).stdout

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def test_committed_uncommitted_and_untracked_changes_all_count(self):
        self.write("docs/notes.md", "Changed.\n")
        self.git("commit", "-qam", "docs")
        self.assertEqual("G0", selection.plan_for(self.root, "pull_request", self.base)["gate"])
        self.write("data/src/main/Store.kt", "class Store\n")
        self.assertEqual(["data", "app"], selection.plan_for(self.root, "pull_request", self.base)["modules"])

    def test_a_file_moved_out_of_the_code_keeps_its_module_selected(self):
        self.git("mv", "app/src/main/Main.kt", "docs/Main.md")
        plan = selection.plan_for(self.root, "pull_request", self.base)
        self.assertEqual(("G2", ["app"]), (plan["gate"], plan["modules"]))
        self.assertIn("app/src/main/Main.kt", plan["changed"])

    def test_an_unknown_base_selects_everything_instead_of_nothing(self):
        plan = selection.plan_for(self.root, "pull_request", "0" * 40)
        self.assertEqual(("G2", ALL), (plan["gate"], plan["modules"]))


class ModuleGraphTests(unittest.TestCase):
    def test_module_partition_changes_cannot_silently_outgrow_the_selection_graph(self):
        root = Path(__file__).resolve().parents[2]
        expected = {"domain": set(), "data": {"domain"}, "design-system": set(),
                    "app": {"domain", "data", "design-system"}, "catalog": {"design-system"},
                    "prototype": {"design-system"}}
        settings = (root / "settings.gradle.kts").read_text()
        included = set(re.findall(r'"\:([\w-]+)"', "\n".join(re.findall(r"include\((.*?)\)", settings, re.S))))
        self.assertEqual(set(expected), included)
        for module, dependencies in expected.items():
            actual = set(re.findall(r'project\("\:([\w-]+)"\)', (root / module / "build.gradle.kts").read_text()))
            self.assertEqual(dependencies, actual, f"Update/review the selection graph for {module}")
        consumers = {module: {m for m in selection.MODULES if module == m or module in expected.get(m, set())
                              or any(module in expected.get(d, set()) for d in expected.get(m, set()))}
                     for module in selection.DEPENDENTS}
        for module, suites in selection.DEPENDENTS.items():
            self.assertEqual(consumers[module], set(suites), module)


if __name__ == "__main__":
    unittest.main()
