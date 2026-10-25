package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Income
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioDefinitionTest {
    @Test
    fun eachStepHasItsOwnOperationAndTheSameOneEveryTime() {
        Scenarios.all.forEach { scenario ->
            val ids = scenario.steps.indices.map(scenario::operation)
            assertEquals(scenario.key, ids.size, ids.toSet().size)
            assertEquals(ids, scenario.steps.indices.map(scenario::operation))
            ids.forEach {
                assertTrue(it, Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(it))
            }
        }
        // A new version of a scenario is a new set of operations.
        val other = Scenarios.G01.copy(version = Scenarios.G01.version + 1)
        assertTrue(Scenarios.G01.operation(1) != other.operation(1))
    }

    @Test
    fun datesNeverGoBackAndEveryEntryHasACategoryCreatedBeforeIt() {
        Scenarios.all.forEach { scenario ->
            var today = scenario.start
            val categories = mutableSetOf<String>()
            val goals = mutableSetOf<String>()
            val goalNames = mutableSetOf<String>()
            scenario.steps.forEachIndexed { index, step ->
                when (step) {
                    is AdvanceTo -> {
                        assertTrue("${scenario.key} $step", step.date > today)
                        today = step.date
                    }

                    is Category -> assertTrue(categories.add(step.key))

                    is Expense -> assertTrue("${scenario.key} $step", step.category in categories)

                    is Income -> Unit

                    // A step that names another names an earlier one of the right kind.
                    is ScenarioStep.Repeat -> assertTrue(
                        scenario.steps[step.of] !is AdvanceTo && step.of < index
                    )

                    is ScenarioStep.EditExpense -> assertTrue(
                        scenario.steps[step.of] is Expense && step.of < index
                    )

                    is ScenarioStep.DeleteExpense -> assertTrue(
                        scenario.steps[step.of] is Expense && step.of < index
                    )

                    is ScenarioStep.UndoDelete ->
                        assertTrue(
                            scenario.steps[step.of] is ScenarioStep.DeleteExpense && step.of < index
                        )

                    is ScenarioStep.SetDefaultLimit -> assertTrue(step.category in categories)

                    is ScenarioStep.SetMonthLimit -> assertTrue(step.category in categories)

                    is ScenarioStep.Archive -> assertTrue(step.category in categories)

                    is ScenarioStep.Verify -> {
                        assertTrue(step.checks.isNotEmpty() || step.goals.isNotEmpty())
                        // Each goal check names a goal made before it, its four places and
                        // the approved example its values come from.
                        step.goals.forEach {
                            assertTrue("${scenario.key} $it", it.goal in goals)
                            assertEquals("${scenario.key} $it", 4, it.stamps.size)
                            assertTrue("${scenario.key} $it", it.source.contains(Regex("MT\\d\\d")))
                        }
                    }

                    // Keys and names are unique: a restored history is checked by name.
                    is ScenarioStep.Goal -> assertTrue(
                        "${scenario.key} $step",
                        goals.add(step.key) && goalNames.add(step.name)
                    )

                    is ScenarioStep.Aporte -> assertTrue(
                        "${scenario.key} $step",
                        step.goal in goals
                    )

                    is ScenarioStep.Retiro -> assertTrue(
                        "${scenario.key} $step",
                        step.goal in goals
                    )

                    is ScenarioStep.GoalTarget ->
                        assertTrue("${scenario.key} $step", step.goal in goals)

                    is ScenarioStep.MarkLograda ->
                        assertTrue("${scenario.key} $step", step.goal in goals)

                    is ScenarioStep.UndoLograda -> assertTrue(
                        scenario.steps[step.of] is ScenarioStep.MarkLograda && step.of < index
                    )

                    is ScenarioStep.ChooseForRecibo ->
                        assertTrue("${scenario.key} $step", step.goal in goals)
                }
            }
        }
    }

    @Test
    fun g01IsTheApprovedExample() {
        val steps = Scenarios.G01.steps
        assertEquals(LocalDate.of(2026, 10, 1), Scenarios.G01.start)
        assertEquals(
            listOf(10_000L, 15_000L, 5_000L),
            steps.filterIsInstance<Expense>().map {
                it.pesos
            }
        )
        assertEquals(listOf(200_000L), steps.filterIsInstance<Income>().map { it.pesos })
        assertEquals(100_000L, steps.filterIsInstance<Category>().single().limit)
    }

    @Test
    fun theSixMonthsAreTheSameOnEveryBuildAndCoverMayToOctober() {
        val scenario = Scenarios.SixMonths
        val expenses = scenario.steps.filterIsInstance<Expense>()
        assertEquals(36, expenses.size)
        assertEquals(6, scenario.steps.filterIsInstance<Income>().size)
        // Pinned: a change to the seed or the steps must come with a new version.
        assertEquals(3_004_000L, expenses.sumOf { it.pesos })
        val months = scenario.steps.filterIsInstance<AdvanceTo>().map {
            YearMonth.from(it.date)
        }.toSet()
        assertEquals((5..10).map { YearMonth.of(2026, it) }.toSet(), months + YearMonth.of(2026, 5))
    }

    @Test
    fun scriptedCategoryVersionsFollowTheChangesBeforeThem() {
        // A category starts at version 1 and each limit change or archive adds one.
        Scenarios.all.forEach { scenario ->
            val versions = mutableMapOf<String, Long>()
            fun changed(category: String, version: Long, step: ScenarioStep) {
                assertEquals("${scenario.key} $step", versions[category], version)
                versions[category] = version + 1
            }
            scenario.steps.forEach { step ->
                when (step) {
                    is Category -> versions[step.key] = 1
                    is ScenarioStep.SetDefaultLimit -> changed(step.category, step.version, step)
                    is ScenarioStep.SetMonthLimit -> changed(step.category, step.version, step)
                    is ScenarioStep.Archive -> changed(step.category, step.version, step)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun everyScenarioEndsByCheckingItsFiguresAndEachFigureNamesItsSource() {
        val unchecked = setOf("seis-meses", "bordes")
        Scenarios.all.filter { it.id !in unchecked }.forEach { scenario ->
            assertTrue(scenario.key, scenario.steps.last() is ScenarioStep.Verify)
        }
        Scenarios.all.flatMap { it.steps }.filterIsInstance<ScenarioStep.Verify>()
            .flatMap { it.checks }
            .forEach { assertTrue("$it", it.source.isNotBlank()) }
    }

    @Test
    fun theCorpusDocumentListsEveryScenarioAndItsVersion() {
        val corpus = java.io.File("../docs/testing/sandbox-scenarios.md").readText()
        Scenarios.all.forEach { assertTrue(it.key, "`${it.key}`" in corpus) }
    }
}
