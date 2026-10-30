package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every history must be before it is loaded: each step names something an earlier
 * step made, dates only move forward and end on the history's day, and each version is
 * the one a screen would have read, so a step tried again sends the same command.
 */
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
        assertEquals(Scenarios.all.size, Scenarios.all.map { it.id }.toSet().size)
        // A new version of a history is a new set of operations.
        val year = Scenarios.NormalYear
        assertTrue(year.operation(1) != year.copy(version = year.version + 1).operation(1))
    }

    @Test
    fun datesOnlyMoveForwardAndEndOnTheHistorysDay() {
        Scenarios.all.forEach { scenario ->
            var today = scenario.start
            scenario.steps.forEach { step ->
                if (step is AdvanceTo) {
                    assertTrue("${scenario.key} $step", step.date > today)
                    today = step.date
                }
                // An entry dated on its own day is never after the sandbox's.
                val on = when (step) {
                    is Expense -> step.on
                    is ScenarioStep.Income -> step.on
                    is ScenarioStep.Aporte -> step.on
                    else -> null
                }
                on?.let { assertTrue("${scenario.key} $step", it <= today && it >= scenario.start) }
            }
            assertEquals(scenario.key, scenario.today, today)
        }
    }

    @Test
    fun everyStepNamesWhatAnEarlierStepMade() {
        Scenarios.all.forEach { scenario ->
            val categories = mutableSetOf<String>()
            val goals = mutableSetOf<String>()
            val schedules = mutableSetOf<String>()
            val investments = mutableSetOf<String>()
            scenario.steps.forEachIndexed { index, step ->
                val named = when (step) {
                    is Category -> categories.add(step.key)

                    is Expense -> step.category in categories

                    is ScenarioStep.EditExpense -> scenario.steps[step.of] is Expense &&
                        step.of < index

                    is ScenarioStep.DeleteExpense ->
                        scenario.steps[step.of] is Expense && step.of < index

                    is ScenarioStep.SetDefaultLimit -> step.category in categories

                    is ScenarioStep.SetMonthLimit -> step.category in categories

                    is ScenarioStep.Archive -> step.category in categories

                    is ScenarioStep.EnableCarry -> step.category in categories

                    is ScenarioStep.DisableCarry -> step.category in categories

                    is ScenarioStep.Goal -> goals.add(step.key)

                    is ScenarioStep.Aporte -> step.goal in goals

                    is ScenarioStep.Retiro -> step.goal in goals

                    is ScenarioStep.GoalTarget -> step.goal in goals

                    is ScenarioStep.MarkLograda -> step.goal in goals

                    is ScenarioStep.ChooseForRecibo -> step.goal in goals

                    is ScenarioStep.Schedule -> schedules.add(step.key) &&
                        (step.entry as? Scheduled.Payment)?.category.let {
                            it == null || it in categories
                        }

                    is ScenarioStep.PauseSchedule -> step.schedule in schedules

                    is ScenarioStep.ResumeSchedule -> step.schedule in schedules

                    is ScenarioStep.Investment -> investments.add(step.key)

                    is ScenarioStep.Movement -> step.investment in investments

                    is AdvanceTo, is ScenarioStep.Income, is ScenarioStep.SetRate,
                    ScenarioStep.CatchUp -> true
                }
                assertTrue("${scenario.key} step ${index + 1}: $step", named)
            }
        }
    }

    @Test
    fun theFirstStepSavesSomethingALaterProcessCanFindByItsReceipt() {
        // How a load carried on in a new process knows its history was not erased.
        Scenarios.all.forEach { scenario ->
            val first = scenario.steps.first()
            assertTrue(
                "${scenario.key} $first",
                first is Category || first is ScenarioStep.Investment || first is ScenarioStep.Goal
            )
        }
    }

    @Test
    fun everyPesoAmountIsOneAnEntryAccepts() {
        Scenarios.all.forEach { scenario ->
            scenario.steps.mapNotNull { step ->
                when (step) {
                    is Category -> step.limit
                    is Expense -> step.pesos
                    is ScenarioStep.EditExpense -> step.pesos
                    is ScenarioStep.Income -> step.pesos
                    is ScenarioStep.Goal -> step.target
                    is ScenarioStep.Aporte -> step.pesos
                    is ScenarioStep.Retiro -> step.pesos
                    is ScenarioStep.Schedule -> step.entry.pesos
                    else -> null
                }
            }.forEach {
                assertTrue(
                    "${scenario.key} $it",
                    TransactionAmount.of(Money.cop(it)) is Outcome.Success
                )
            }
        }
    }

    @Test
    fun scriptedCategoryVersionsFollowTheChangesBeforeThem() {
        // A category starts at version 1 and each limit change, archive or carry change adds one.
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
                    is ScenarioStep.EnableCarry -> changed(step.category, step.version, step)
                    is ScenarioStep.DisableCarry -> changed(step.category, step.version, step)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun scriptedExpenseVersionsAreTheFirst() {
        // An expense starts at version 1; each history corrects or deletes it only once.
        Scenarios.all.forEach { scenario ->
            val touched = scenario.steps.mapNotNull {
                when (it) {
                    is ScenarioStep.EditExpense -> it.of to it.version
                    is ScenarioStep.DeleteExpense -> it.of to it.version
                    else -> null
                }
            }
            assertEquals(
                scenario.key,
                touched.map {
                    it.first
                }.distinct(),
                touched.map { it.first }
            )
            touched.forEach { assertEquals("${scenario.key} $it", 1L, it.second) }
        }
    }

    @Test
    fun scriptedGoalVersionsFollowTheChangesBeforeThem() {
        // A goal starts at version 1; each movement, target change or completion adds one.
        Scenarios.all.forEach { scenario ->
            val versions = mutableMapOf<String, Long>()
            fun changed(goal: String, version: Long, step: ScenarioStep) {
                assertEquals("${scenario.key} $step", versions[goal], version)
                versions[goal] = version + 1
            }
            scenario.steps.forEach { step ->
                when (step) {
                    is ScenarioStep.Goal -> versions[step.key] = 1
                    is ScenarioStep.Aporte -> changed(step.goal, step.version, step)
                    is ScenarioStep.Retiro -> changed(step.goal, step.version, step)
                    is ScenarioStep.GoalTarget -> changed(step.goal, step.version, step)
                    is ScenarioStep.MarkLograda -> changed(step.goal, step.version, step)
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun scriptedScheduleVersionsFollowTheChangesBeforeThem() {
        // A schedule starts at version 1 and each pause or resume adds one.
        Scenarios.all.forEach { scenario ->
            val versions = mutableMapOf<String, Long>()
            fun changed(schedule: String, version: Long, step: ScenarioStep) {
                assertEquals("${scenario.key} $step", versions[schedule], version)
                versions[schedule] = version + 1
            }
            scenario.steps.forEach { step ->
                when (step) {
                    is ScenarioStep.Schedule -> versions[step.key] = 1

                    is ScenarioStep.PauseSchedule -> changed(step.schedule, step.version, step)

                    is ScenarioStep.ResumeSchedule -> changed(step.schedule, step.version, step)

                    // Archiving would pause, and so change, the category's schedules (rule 9).
                    is ScenarioStep.Archive -> assertTrue(
                        "${scenario.key} $step",
                        scenario.steps.filterIsInstance<ScenarioStep.Schedule>().none {
                            (it.entry as? Scheduled.Payment)?.category == step.category
                        }
                    )

                    else -> Unit
                }
            }
        }
    }

    @Test
    fun scriptedInvestmentVersionsFollowTheMovementsBeforeThem() {
        // An investment starts at version 1, each movement adds one, a rate changes none.
        Scenarios.all.forEach { scenario ->
            val versions = mutableMapOf<String, Long>()
            scenario.steps.forEach { step ->
                when (step) {
                    is ScenarioStep.Investment -> versions[step.key] = 1

                    is ScenarioStep.Movement -> {
                        assertEquals(
                            "${scenario.key} $step",
                            versions[step.investment],
                            step.version
                        )
                        versions[step.investment] = step.version + 1
                    }

                    else -> Unit
                }
            }
        }
    }

    @Test
    fun catchUpRunsAfterEveryDayChangeAndEverySaveThatCanMakeSomethingDue() {
        // Sello runs catch-up by itself then; a step waits for it, so nothing races the
        // next step and a load cut short and carried on writes in the same order.
        Scenarios.all.forEach { scenario ->
            val first = scenario.steps.indexOfFirst { it is ScenarioStep.Schedule }
            if (first < 0) return@forEach
            scenario.steps.forEachIndexed { index, step ->
                val due = (step is AdvanceTo && index > first) ||
                    step is ScenarioStep.Schedule || step is ScenarioStep.ResumeSchedule
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
    fun theSandboxDocumentDescribesEveryHistoryAndItsVersion() {
        val doc = java.io.File("../docs/testing/sandbox-scenarios.md").readText()
        Scenarios.all.forEach {
            assertTrue(it.key, "`${it.key}`" in doc)
            assertTrue(it.key, it.title in doc)
        }
    }
}
