package com.software.sello.feature.expense

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.StorageFailure
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.HandGuidance
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Anotar varios nudge under Otro gasto (SELLO-212), with a controlled clock: Thursday 22
 * October 2026 in Bogotá (UTC−5). Expenses "added today" were added at 10:00 there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEntryNudgeTest {
    private val reads = HandExpenseReads()
    private val snapshots = HandSnapshots()
    private val scheduleBook = HandScheduleReads()
    private val clock = HandFinancialClock("2026-10-22")
    private var guidance = HandGuidance()
    private val food = categoryIn(1, "Alimentación", 900_000)
    private val oct22 = LocalDate.parse("2026-10-22")

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    /** An expense dated today, added today at 10:00 in Bogotá. */
    private fun addedToday(number: Int, pesos: Long) = storedExpense(number, 1, "2026-10-22", pesos)
        .copy(createdAt = Instant.parse("2026-10-22T15:00:00Z"))

    private fun stored(vararg expenses: com.software.sello.domain.model.Expense) =
        expenses.forEach { reads.stored[it.id] = it }

    private fun form(): ExpenseEntryViewModel {
        val commands = HandExpenseCommands()
        return ExpenseEntryViewModel(
            SavedStateHandle(),
            commands,
            reads,
            snapshots,
            clock,
            { uuid(500) },
            testMoney(),
            Corrections(
                commands,
                HandIncomeCommands(),
                HandGoalCommands(),
                HandInvestmentCommands(),
                { Duration.ZERO },
                { uuid(600) },
                CoroutineScope(Dispatchers.Main),
                HandCategoryReads(),
                testMoney()
            ),
            HandScheduleCommands(scheduleBook),
            scheduleBook,
            Dispatchers.Main,
            guidance
        )
    }

    private fun ready(): ExpenseEntryViewModel {
        snapshots.ready(monthOf("2026-10-22", listOf(food)))
        return form()
    }

    private val ExpenseEntryViewModel.nudge get() = state.value.severalNudge

    @Test
    fun threeAddedByHandTodayPointAtOtroGasto() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        val form = ready()

        assertTrue(form.nudge)
        assertEquals(listOf("shown BulkEntry 2026-10-22"), guidance.writes)
    }

    @Test
    fun twoAreNotEnough() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000))
        val form = ready()

        assertFalse(form.nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun usingTheSlipClosesItForGood() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        val form = ready()

        form.onAction(ExpenseEntryAction.Several)

        assertFalse(form.nudge)
        assertEquals("seen BulkEntry", guidance.writes.last())
    }

    @Test
    fun usingTheSlipBeforeItShowedClosesItToo() {
        val form = ready()

        form.onAction(ExpenseEntryAction.Several)

        assertEquals(listOf("seen BulkEntry"), guidance.writes)
    }

    @Test
    fun itsCrossClosesItForGoodAndItGoesEvenWhenThatCannotBeKept() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        val form = ready()
        guidance.failWrites = true

        form.onAction(ExpenseEntryAction.CloseSeveralNudge)

        assertFalse(form.nudge)
        assertEquals("seen BulkEntry", guidance.writes.last())
    }

    @Test
    fun expensesThatCouldNotBeReadOrWereReadInPartShowNone() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        reads.rangeFailure = StorageFailure.Unavailable("SQLiteException")
        assertFalse(ready().nudge)

        reads.rangeFailure = null
        reads.rangeLeftSomeOut = true
        assertFalse(ready().nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun guidanceThatCannotBeReadShowsNone() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        guidance.failReads()

        assertFalse(ready().nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun noneBeforeSelloCheckedWhoWasAlreadyUsingIt() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        guidance = HandGuidance(Guidance(existingUseChecked = false))

        assertFalse(ready().nudge)
    }

    @Test
    fun peopleAlreadyUsingSelloSeeNone() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        guidance.set(GuidanceMemory(seen = Nudge.entries.filter { it.forNewUsers }.toSet()))

        assertFalse(ready().nudge)
    }

    @Test
    fun noneWhileTheFormLoadsAndNothingIsKeptAsShown() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        val form = form()

        assertFalse(form.nudge)
        assertEquals(emptyList<String>(), guidance.writes)

        snapshots.ready(monthOf("2026-10-22", listOf(food)))
        assertTrue(form.nudge)
    }

    @Test
    fun anotherNudgeShownTodayKeepsTheDay() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.Deleting, oct22)))

        assertFalse(ready().nudge)
    }

    @Test
    fun itShowsForOneDayOnly() {
        stored(addedToday(100, 4_500), addedToday(101, 12_000), addedToday(102, 8_000))
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.BulkEntry, oct22.minusDays(1))))

        assertFalse(ready().nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }
}
