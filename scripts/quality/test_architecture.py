import tempfile
import unittest
from pathlib import Path

import architecture


def write(root, relative, text):
    path = Path(root) / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

SETTINGS = 'include(":app")\ninclude(":catalog")\ninclude(":data")\ninclude(":design-system")\ninclude(":domain")\n'
BUILDS = {
    "app": 'dependencies {\n    implementation(project(":domain"))\n    implementation(project(":design-system"))\n    implementation(project(":data"))\n}\n',
    "catalog": 'dependencies {\n    implementation(project(":design-system"))\n}\n',
    "data": 'dependencies {\n    implementation(project(":domain"))\n}\n',
    "design-system": "dependencies {\n}\n",
    "domain": "dependencies {\n}\n",
}
APP = "app/src/main/java/com/software/sello"


class ArchitectureTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        write(self.root, "settings.gradle.kts", SETTINGS)
        for module, text in BUILDS.items():
            write(self.root, f"{module}/build.gradle.kts", text)
        write(self.root, "domain/src/main/kotlin/com/software/sello/domain/model/Money.kt",
              "package com.software.sello.domain.model\n\nimport kotlinx.coroutines.flow.Flow\nimport java.time.LocalDate\n\nclass Money\n")
        write(self.root, "data/src/main/java/com/software/sello/data/local/ExpenseDao.kt",
              "package com.software.sello.data.local\n\nimport androidx.room.Dao\nimport com.software.sello.domain.model.Money\n\ninterface ExpenseDao\n")
        write(self.root, "design-system/src/main/java/com/software/sello/designsystem/theme/Theme.kt",
              "package com.software.sello.designsystem.theme\n\nimport androidx.compose.runtime.Composable\n")
        write(self.root, f"{APP}/composition/AppGraph.kt",
              "package com.software.sello.composition\n\nimport com.software.sello.data.local.ExpenseDao\nimport com.software.sello.feature.expense.ExpenseScreen\n")
        write(self.root, f"{APP}/feature/expense/ExpenseScreen.kt",
              "package com.software.sello.feature.expense\n\nimport com.software.sello.domain.model.Money\nimport com.software.sello.designsystem.theme.SelloTheme\n")
        write(self.root, "app/src/debug/java/com/software/sello/devtools/Sandbox.kt",
              "package com.software.sello.devtools\n\nimport com.software.sello.feature.expense.ExpenseScreen\n")
        write(self.root, "catalog/src/main/java/com/software/sello/catalog/CatalogActivity.kt",
              "package com.software.sello.catalog\n\nimport com.software.sello.designsystem.theme.SelloTheme\n")

    def found(self):
        return [str(violation) for violation in architecture.violations(self.root)]

    def assert_violation(self, rule, location):
        found = self.found()
        self.assertEqual(1, len(found), found)
        self.assertIn(rule, found[0])
        self.assertTrue(found[0].startswith(location), found[0])

    def test_allowed_graph_has_no_violations(self):
        self.assertEqual([], self.found())

    def test_domain_cannot_import_android(self):
        path = "domain/src/main/kotlin/com/software/sello/domain/model/Bad.kt"
        write(self.root, path, "package com.software.sello.domain.model\n\nimport android.os.Bundle\n")
        self.assert_violation("domain-pure", f"{path}:3")

    def test_domain_cannot_use_storage_di_or_http_libraries(self):
        for library in ("androidx.room.Entity", "org.koin.core.module.Module", "io.ktor.client.HttpClient"):
            with self.subTest(library):
                path = "domain/src/main/kotlin/com/software/sello/domain/model/Bad.kt"
                write(self.root, path, f"package com.software.sello.domain.model\n\nimport {library}\n")
                self.assert_violation("domain-pure", f"{path}:3")

    def test_domain_tests_cannot_use_android_either(self):
        path = "domain/src/test/kotlin/com/software/sello/domain/MoneyTest.kt"
        write(self.root, path, "package com.software.sello.domain\n\nimport androidx.test.core.app.ApplicationProvider\n")
        self.assert_violation("domain-pure", f"{path}:3")

    def test_domain_cannot_reference_another_module(self):
        path = "domain/src/main/kotlin/com/software/sello/domain/model/Bad.kt"
        write(self.root, path, "package com.software.sello.domain.model\n\nimport com.software.sello.data.local.ExpenseDao\n")
        self.assert_violation("module-boundary", f"{path}:3")

    def test_design_system_cannot_reference_domain(self):
        path = "design-system/src/main/java/com/software/sello/designsystem/money/Amount.kt"
        write(self.root, path, "package com.software.sello.designsystem.money\n\nimport com.software.sello.domain.model.Money\n")
        self.assert_violation("module-boundary", f"{path}:3")

    def test_catalog_cannot_reference_app_or_domain(self):
        path = "catalog/src/main/java/com/software/sello/catalog/Bad.kt"
        write(self.root, path, "package com.software.sello.catalog\n\nimport com.software.sello.feature.expense.ExpenseScreen\n")
        self.assert_violation("module-boundary", f"{path}:3")

    def test_feature_cannot_import_data(self):
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nimport com.software.sello.data.local.ExpenseDao\n")
        self.assert_violation("data-composition-only", f"{path}:3")

    def test_aliased_data_import_is_caught(self):
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nimport com.software.sello.data.local.ExpenseDao as Storage\n")
        self.assert_violation("data-composition-only", f"{path}:3")

    def test_wildcard_data_import_is_caught(self):
        path = f"{APP}/platform/Bad.kt"
        write(self.root, path, "package com.software.sello.platform\n\nimport com.software.sello.data.local.*\n")
        self.assert_violation("data-composition-only", f"{path}:3")

    def test_fully_qualified_data_reference_is_caught(self):
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nclass Bad {\n    val dao: com.software.sello.data.local.ExpenseDao? = null\n}\n")
        self.assert_violation("data-composition-only", f"{path}:4")

    def test_data_named_in_comments_and_strings_is_not_a_reference(self):
        write(self.root, f"{APP}/feature/expense/Fine.kt",
              'package com.software.sello.feature.expense\n\n// see com.software.sello.data.local.ExpenseDao\n'
              '/* com.software.sello.data */\nval note = "com.software.sello.data.local"\nval raw = """com.software.sello.data"""\n')
        self.assertEqual([], self.found())

    def test_composition_must_live_in_the_composition_directory(self):
        path = f"{APP}/feature/expense/Sneaky.kt"
        write(self.root, path, "package com.software.sello.composition\n\nimport com.software.sello.data.local.ExpenseDao\n")
        self.assert_violation("data-composition-only", f"{path}:3")

    def test_feature_cannot_reach_composition(self):
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nimport com.software.sello.composition.AppGraph\n")
        self.assert_violation("composition-is-root", f"{path}:3")

    def test_feature_cannot_import_another_feature(self):
        write(self.root, f"{APP}/feature/income/IncomeScreen.kt", "package com.software.sello.feature.income\n\nclass IncomeScreen\n")
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nimport com.software.sello.feature.income.IncomeScreen\n")
        self.assert_violation("feature-isolation", f"{path}:3")

    def test_feature_may_reference_its_own_subpackages(self):
        write(self.root, f"{APP}/feature/expense/form/Form.kt",
              "package com.software.sello.feature.expense.form\n\nimport com.software.sello.feature.expense.ExpenseScreen\n")
        self.assertEqual([], self.found())

    def test_customer_app_cannot_reference_catalog(self):
        path = f"{APP}/navigation/Bad.kt"
        write(self.root, path, "package com.software.sello.navigation\n\nimport com.software.sello.catalog.CatalogActivity\n")
        self.assert_violation("module-boundary", f"{path}:3")

    def test_release_sources_cannot_reference_debug_tools(self):
        path = f"{APP}/navigation/Bad.kt"
        write(self.root, path, "package com.software.sello.navigation\n\nimport com.software.sello.devtools.Sandbox\n")
        self.assert_violation("debug-tools-in-release", f"{path}:3")

    def test_debug_tools_cannot_be_declared_outside_debug_source_sets(self):
        path = f"{APP}/devtools/Sandbox.kt"
        write(self.root, path, "package com.software.sello.devtools\n\nclass Leaked\n")
        self.assert_violation("debug-tools-in-release", f"{path}:1")

    def test_app_cannot_depend_on_catalog_module(self):
        write(self.root, "app/build.gradle.kts", BUILDS["app"].replace("}\n", '    implementation(project(":catalog"))\n}\n'))
        self.assert_violation("module-edge", "app/build.gradle.kts:5")

    def test_design_system_cannot_depend_on_domain_module(self):
        write(self.root, "design-system/build.gradle.kts", 'dependencies {\n    api(project(path = ":domain"))\n}\n')
        self.assert_violation("module-edge", "design-system/build.gradle.kts:2")

    def test_domain_cannot_depend_on_data_module(self):
        write(self.root, "domain/build.gradle.kts", 'dependencies {\n    implementation(project(":data"))\n}\n')
        self.assert_violation("module-edge", "domain/build.gradle.kts:2")

    def test_commented_out_edge_is_ignored(self):
        write(self.root, "domain/build.gradle.kts", 'dependencies {\n    // implementation(project(":data"))\n}\n')
        self.assertEqual([], self.found())

    def test_typesafe_project_accessor_is_rejected_as_unreadable(self):
        write(self.root, "catalog/build.gradle.kts", "dependencies {\n    implementation(projects.app)\n}\n")
        self.assert_violation("module-edge", "catalog/build.gradle.kts:2")

    def test_new_module_needs_an_explicit_rule(self):
        write(self.root, "settings.gradle.kts", SETTINGS + 'include(":dev-tools")\n')
        self.assert_violation("unknown-module", "settings.gradle.kts:6")

    def test_wall_clock_reads_are_only_allowed_in_the_platform_package(self):
        reads = ("Instant.now()", "LocalDate.now()", "YearMonth.now(zone)", "System.currentTimeMillis()",
                 "System.nanoTime()", "SystemClock.elapsedRealtime()", "ZoneId.systemDefault()",
                 "TimeZone.getDefault()", "Clock.systemUTC()")
        for read in reads:
            with self.subTest(read):
                path = f"{APP}/feature/expense/Bad.kt"
                write(self.root, path, f"package com.software.sello.feature.expense\n\nval value = {read}\n")
                self.assert_violation("ad-hoc-time", f"{path}:3")

    def test_wall_clock_reads_are_rejected_in_every_module(self):
        for module, path in (
            ("domain", "domain/src/main/kotlin/com/software/sello/domain/policy/Bad.kt"),
            ("data", "data/src/main/java/com/software/sello/data/local/Bad.kt"),
            ("composition", f"{APP}/composition/Bad.kt"),
        ):
            with self.subTest(module):
                package = path.split("/com/software/sello/")[1].rsplit("/", 1)[0].replace("/", ".")
                write(self.root, path, f"package com.software.sello.{package}\n\nval today = java.time.LocalDate.now()\n")
                self.assert_violation("ad-hoc-time", f"{path}:3")
                (self.root / path).unlink()

    def test_platform_package_may_read_the_wall_clock(self):
        write(self.root, f"{APP}/platform/SystemClocks.kt",
              "package com.software.sello.platform\n\nimport java.time.Instant\n\nfun now() = Instant.now()\nval zone = java.time.ZoneId.systemDefault()\n")
        self.assertEqual([], self.found())

    def test_tests_may_build_their_own_instants(self):
        write(self.root, "app/src/test/java/com/software/sello/platform/ClockTest.kt",
              "package com.software.sello.platform\n\nval started = System.nanoTime()\n")
        self.assertEqual([], self.found())

    def test_global_scope_is_rejected_everywhere(self):
        path = f"{APP}/platform/Bad.kt"
        write(self.root, path, "package com.software.sello.platform\n\nimport kotlinx.coroutines.GlobalScope\n")
        self.assert_violation("global-coroutines", f"{path}:3")

    def test_global_dispatchers_are_only_named_in_the_platform_package(self):
        path = f"{APP}/feature/expense/Bad.kt"
        write(self.root, path, "package com.software.sello.feature.expense\n\nimport kotlinx.coroutines.Dispatchers\n\nval io = Dispatchers.IO\n")
        found = self.found()
        self.assertEqual(["global-coroutines"], sorted({line.split("[")[1].split("]")[0] for line in found}))
        write(self.root, f"{APP}/platform/Dispatchers.kt", "package com.software.sello.platform\n\nval io = kotlinx.coroutines.Dispatchers.IO\n")
        (self.root / path).unlink()
        self.assertEqual([], self.found())

    def test_koin_is_only_used_from_composition(self):
        for path in (f"{APP}/feature/expense/Bad.kt", f"{APP}/platform/Bad.kt", "data/src/main/java/com/software/sello/data/Bad.kt"):
            with self.subTest(path):
                package = path.split("/com/software/sello/")[1].rsplit("/", 1)[0].replace("/", ".")
                write(self.root, path, f"package com.software.sello.{package}\n\nimport org.koin.core.component.KoinComponent\n")
                self.assert_violation("di-composition-only", f"{path}:3")
                (self.root / path).unlink()

    def test_composition_and_debug_tools_may_define_koin_modules(self):
        write(self.root, f"{APP}/composition/Modules.kt", "package com.software.sello.composition\n\nimport org.koin.dsl.module\n")
        write(self.root, "app/src/debug/java/com/software/sello/devtools/Override.kt", "package com.software.sello.devtools\n\nimport org.koin.dsl.module\n")
        self.assertEqual([], self.found())

    def test_java_sources_are_checked_too(self):
        path = "domain/src/main/java/com/software/sello/domain/Bad.java"
        write(self.root, path, "package com.software.sello.domain;\n\nimport android.os.Bundle;\n")
        self.assert_violation("domain-pure", f"{path}:3")


if __name__ == "__main__":
    unittest.main()
