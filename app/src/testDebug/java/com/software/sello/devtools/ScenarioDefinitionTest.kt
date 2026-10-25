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

private val RE = Regex("RE\\d\\d")

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
            val schedules = mutableSetOf<String>()
            val notes = mutableSetOf<String>()
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
                        assertTrue(step.figures > 0)
                        // Each recurrence check names a schedule made before it and the
                        // recurrence example its values come from.
                        (
                            step.occurrences.map { it.schedule to it.source } +
                                step.schedules.map { it.schedule to it.source }
                            )
                            .forEach { (schedule, source) ->
                                assertTrue("${scenario.key} $schedule", schedule in schedules)
                                assertTrue("${scenario.key} $source", source.contains(RE))
                            }
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

                    // Keys and notes are unique: a restored history is checked by note.
                    is ScenarioStep.Schedule -> {
                        assertTrue(
                            "${scenario.key} $step",
                            schedules.add(step.key) && notes.add(step.entry.note)
                        )
                        (step.entry as? Scheduled.Payment)?.let {
                            assertTrue("${scenario.key} $step", it.category in categories)
                        }
                    }

                    is ScenarioStep.ChangeSchedule ->
                        assertTrue("${scenario.key} $step", step.schedule in schedules)

                    is ScenarioStep.PauseSchedule ->
                        assertTrue("${scenario.key} $step", step.schedule in schedules)

                    is ScenarioStep.ResumeSchedule ->
                        assertTrue("${scenario.key} $step", step.schedule in schedules)

                    is ScenarioStep.DeleteSchedule ->
                        assertTrue("${scenario.key} $step", step.schedule in schedules)

                    is ScenarioStep.DeleteOccurrence -> assertTrue(
                        "${scenario.key} $step",
                        step.schedule in schedules && step.date <= today
                    )

                    is ScenarioStep.Unarchive ->
                        assertTrue("${scenario.key} $step", step.category in categories)

                    ScenarioStep.CatchUp -> Unit
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
                    is ScenarioStep.Unarchive -> changed(step.category, step.version, step)
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
    fun theRecurrenceExamplesTheSandboxCoversEachHaveACheck() {
        val named = Scenarios.all.flatMap { it.steps }.filterIsInstance<ScenarioStep.Verify>()
            .flatMap { verify ->
                verify.occurrences.map { it.source } + verify.schedules.map { it.source }
            }
            .flatMap { source -> RE.findAll(source).map { it.value }.toList() }
            .toSet()
        val covered = listOf(1, 5, 6, 7, 9, 10, 11, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
            .map { "RE%02d".format(it) }
        assertEquals(emptyList<String>(), covered.filter { it !in named })
    }

    @Test
    fun catchUpRunsAfterEveryDayChangeAndEverySaveThatCanMakeSomethingDue() {
        // Sello runs catch-up by itself then; a step waits for it, so nothing races the
        // next step and both runs of the corpus write in the same order.
        Scenarios.all.forEach { scenario ->
            val first = scenario.steps.indexOfFirst { it is ScenarioStep.Schedule }
            if (first < 0) return@forEach
            scenario.steps.forEachIndexed { index, step ->
                val due = (step is ScenarioStep.AdvanceTo && index > first) ||
                    (step is ScenarioStep.Schedule && step.expect == Expect.Saved) ||
                    (step is ScenarioStep.ResumeSchedule && step.expect == Expect.Saved)
                if (due) {
                    assertEquals(
                        "${scenario.key} step ${index + 1}",
                        ScenarioStep.CatchUp,
                        scenario.steps.getOrNull(index + 1)
                    )
                }
            }
        }
    }

    @Test
    fun scriptedScheduleVersionsFollowTheChangesBeforeThem() {
        // A schedule starts at version 1; each saved change, pause or resume adds one, and
        // archiving its category adds one when that pauses it (rule 9).
        Scenarios.all.forEach { scenario ->
            val versions = mutableMapOf<String, Long>()
            val paused = mutableSetOf<String>()
            val categoryOf = mutableMapOf<String, String?>()
            fun read(schedule: String, version: Long, saved: Boolean, step: ScenarioStep) {
                assertEquals("${scenario.key} $step", versions[schedule], version)
                if (saved) versions[schedule] = version + 1
            }
            scenario.steps.forEach { step ->
                when (step) {
                    is ScenarioStep.Schedule -> {
                        versions[step.key] = 1
                        categoryOf[step.key] = (step.entry as? Scheduled.Payment)?.category
                    }

                    is ScenarioStep.ChangeSchedule ->
                        read(step.schedule, step.version, step.expect == Expect.Saved, step)

                    is ScenarioStep.PauseSchedule -> {
                        read(step.schedule, step.version, step.expect == Expect.Saved, step)
                        if (step.expect == Expect.Saved) paused += step.schedule
                    }

                    is ScenarioStep.ResumeSchedule -> {
                        read(step.schedule, step.version, step.expect == Expect.Saved, step)
                        if (step.expect == Expect.Saved) paused -= step.schedule
                    }

                    is ScenarioStep.DeleteSchedule -> {
                        read(step.schedule, step.version, false, step)
                        versions.remove(step.schedule)
                    }

                    is ScenarioStep.Archive ->
                        versions.keys
                            .filter { categoryOf[it] == step.category && it !in paused }
                            .forEach {
                                versions[it] = versions.getValue(it) + 1
                                paused += it
                            }

                    else -> Unit
                }
            }
        }
    }

    @Test
    fun theLongGapIsRe11() {
        // RE11: every day from 1 ago to 24 oct 2026, 85 occurrences, 425.000.
        val check = Scenarios.all.flatMap { it.steps }.filterIsInstance<ScenarioStep.Verify>()
            .flatMap { it.occurrences }
            .single { it.source.startsWith("RE11") && it.entries.size > 1 }
        assertEquals(85, check.entries.size)
        assertEquals(425_000L, check.entries.sumOf { it.pesos })
        assertTrue(check.entries.all { it.pesos == 5_000L && it.slot == 1 })
        assertEquals(LocalDate.of(2026, 8, 1), check.entries.first().date)
        assertEquals(LocalDate.of(2026, 10, 24), check.entries.last().date)
        assertEquals(85, check.entries.map { it.date }.toSet().size)
    }

    @Test
    fun theCorpusDocumentListsEveryScenarioAndItsVersion() {
        val corpus = java.io.File("../docs/testing/sandbox-scenarios.md").readText()
        Scenarios.all.forEach { assertTrue(it.key, "`${it.key}`" in corpus) }
    }
}
