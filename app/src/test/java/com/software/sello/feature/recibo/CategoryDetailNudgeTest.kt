package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandExpenseReads
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.SavedCarry
import com.software.sello.presentation.guidance.DoneNudges
import com.software.sello.testing.HandGuidance
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Category detail's nudges (SELLO-212) with a controlled clock: Sunday 8 November 2026, the
 * month in progress. Comida (1) has a 500.000 limit and one expense of 20.000 on the 3rd.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailNudgeTest {
    private val november = YearMonth.of(2026, 11)
    private val october = YearMonth.of(2026, 10)
    private val clock = HandFinancialClock("2026-11-08")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val insights = HandCategoryInsights()
    private val schedules = HandScheduleReads()
    private val reads = HandExpenseReads()
    private val commands = HandExpenseCommands()
    private var guidance = HandGuidance()
    private val comida = categoryIn(1, "Comida", 500_000)
    private val lunch = storedExpense(100, 1, "2026-11-03", 20_000)
    private lateinit var corrections: Corrections

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    /** November with [expenses]; its detail figures carry October as [october] has it. */
    private fun publish(
        expenses: List<Expense> = listOf(lunch),
        october: CategoryHistoryMonth? = null
    ) {
        reads.stored.putAll(expenses.associateBy { it.id })
        val snapshot = monthOf("2026-11-08", listOf(comida), expenses, november)
        snapshots.ready(snapshot)
        insights.state.value = ReadState.Ready(figures(snapshot, listOfNotNull(october)))
        schedules.state.value = ReadState.Ready(ScheduleBook(1, snapshot.revision, emptyList()))
    }

    private fun figures(snapshot: MonthlySnapshot, history: List<CategoryHistoryMonth>) =
        CategoryInsights(
            budget = snapshot,
            categoryId = categoryId(1),
            allowance = null,
            comparison = null,
            elapsedDays = 8,
            averageExpense = null,
            largestExpense = null,
            mostExpensiveWeekday = null,
            weeklyAverage = null,
            weeks = emptyList(),
            historicalAverage = null,
            history = history
        )

    /** October's limit and spending, as Comida's history has it. */
    private fun october(limit: Long, spent: Long) =
        CategoryHistoryMonth(october, Money.cop(spent), Money.cop(limit))

    private fun netflix(number: Int, date: String) =
        storedExpense(number, 1, date, 44_900, note = "Netflix")

    private fun pagoFijo(note: String, pesos: Long) = StoredSchedule(
        Schedule(
            scheduleId(1),
            ScheduledEntry.Payment(
                categoryId(1),
                (TransactionAmount.of(Money.cop(pesos)) as Outcome.Success).value,
                (Note.of(note) as Outcome.Success).value
            ),
            (
                Recurrence.of(
                    (RepeatRule.Monthly.of(5) as Outcome.Success).value,
                    LocalDate.parse("2026-11-05")
                ) as Outcome.Success
                ).value
        ),
        sequence = 1,
        handled = null,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun TestScope.started(): CategoryDetailViewModel {
        corrections = Corrections(
            commands,
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { testScheduler.currentTime.milliseconds },
            { uuid(900 + testScheduler.currentTime.toInt()) },
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        val viewModel = CategoryDetailViewModel(
            session,
            snapshots,
            reads,
            testMoney(),
            corrections,
            insights,
            schedules,
            { testScheduler.currentTime.milliseconds },
            commands,
            { uuid(901) },
            SavedStateHandle(),
            guidance,
            clock
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        viewModel.onAction(CategoryDetailAction.Show(uuid(1)))
        return viewModel
    }

    private val CategoryDetailViewModel.nudge
        get() = (state.value as CategoryDetailState.Ready).nudge

    private val nov8 = LocalDate.parse("2026-11-08")

    // Deleting

    @Test
    fun theFirstListedExpenseBringsTheDeletingNudgeWithItsSixSeconds() = runTest {
        publish()
        val viewModel = started()

        assertEquals(DetailNudge.Deleting(6), viewModel.nudge)
        assertEquals(listOf("shown Deleting 2026-11-08"), guidance.writes)
    }

    @Test
    fun withNoExpenseListedThereIsNoNudge() = runTest {
        publish(expenses = emptyList())
        val viewModel = started()

        assertNull(viewModel.nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun deletingAnExpenseClosesItForGood() = runTest {
        publish()
        val viewModel = started()
        DoneNudges(corrections.nudgesDone, guidance, backgroundScope)

        viewModel.onAction(CategoryDetailAction.Delete(lunch.id.value))

        assertNull(viewModel.nudge)
        runCurrent()
        assertTrue(Nudge.Deleting in guidance.stored.memory.seen)
    }

    @Test
    fun itsCrossClosesItForGoodAndItGoesEvenWhenThatCannotBeKept() = runTest {
        publish()
        val viewModel = started()
        guidance.failWrites = true

        viewModel.onAction(CategoryDetailAction.CloseNudge)

        assertNull(viewModel.nudge)
        assertEquals("seen Deleting", guidance.writes.last())
    }

    // Arrastre

    @Test
    fun octoberEndedUnderItsLimitSoArrastreComesFirst() = runTest {
        // October: 500.000 limit, 420.000 spent: 80.000 left.
        publish(october = october(500_000, 420_000))
        val viewModel = started()

        assertEquals(DetailNudge.Carry(october, surplus = true), viewModel.nudge)
        assertEquals(listOf("shown Carry 2026-11-08"), guidance.writes)
    }

    @Test
    fun octoberEndedOverItsLimitSoWhatWasMissingIsSaid() = runTest {
        // October: 500.000 limit, 530.000 spent: 30.000 missing.
        publish(october = october(500_000, 530_000))
        val viewModel = started()

        assertEquals(DetailNudge.Carry(october, surplus = false), viewModel.nudge)
    }

    @Test
    fun endingOctoberExactlyOnItsLimitIsNoReason() = runTest {
        publish(october = october(500_000, 500_000))
        val viewModel = started()

        assertEquals(DetailNudge.Deleting(6), viewModel.nudge)
    }

    @Test
    fun turningArrastreOnAnywhereClosesItAndTheDayStaysItsOwn() = runTest {
        publish(october = october(500_000, 420_000))
        val viewModel = started()
        DoneNudges(corrections.nudgesDone, guidance, backgroundScope)

        corrections.categoryChanged(
            CorrectionNotice.CategorySaved(null, SavedCarry("Transporte", on = true, november))
        )

        // The day belonged to arrastre: no other nudge takes it.
        assertNull(viewModel.nudge)
        runCurrent()
        assertTrue(Nudge.Carry in guidance.stored.memory.seen)
    }

    @Test
    fun turningArrastreOffDoesNotCloseIt() = runTest {
        publish(october = october(500_000, 420_000))
        val viewModel = started()

        corrections.categoryChanged(
            CorrectionNotice.CategorySaved(null, SavedCarry("Transporte", on = false, november))
        )

        assertEquals(DetailNudge.Carry(october, surplus = true), viewModel.nudge)
    }

    @Test
    fun aPastMonthNeverPointsAtArrastre() = runTest {
        // Viewing October on 8 November, with September having ended under its limit.
        val oct = storedExpense(101, 1, "2026-10-03", 20_000)
        reads.stored[oct.id] = oct
        val snapshot = monthOf("2026-11-08", listOf(comida), listOf(oct), october)
        snapshots.ready(snapshot)
        insights.state.value = ReadState.Ready(
            figures(
                snapshot,
                listOf(
                    CategoryHistoryMonth(october.minusMonths(1), Money.cop(1), Money.cop(500_000))
                )
            )
        )
        schedules.state.value = ReadState.Ready(ScheduleBook(1, snapshot.revision, emptyList()))
        session.select(october)
        val viewModel = started()

        assertEquals(DetailNudge.Deleting(6), viewModel.nudge)
    }

    // Pagos fijos

    @Test
    fun theSameExpenseInSeptemberAndOctoberIsAPagoFijo() = runTest {
        publish(
            expenses = listOf(
                lunch,
                netflix(110, "2026-09-05"),
                netflix(111, "2026-10-05")
            )
        )
        val viewModel = started()

        assertEquals(DetailNudge.FixedPayment, viewModel.nudge)
        assertEquals(
            listOf(LocalDate.parse("2026-09-01") to LocalDate.parse("2026-10-31")),
            reads.ranges
        )
    }

    @Test
    fun aPagoFijoForItAlreadyLeavesTheDeletingNudge() = runTest {
        publish(expenses = listOf(lunch, netflix(110, "2026-09-05"), netflix(111, "2026-10-05")))
        schedules.state.value = ReadState.Ready(
            ScheduleBook(
                1,
                (snapshots.of(november).value as ReadState.Ready).value.revision,
                listOf(pagoFijo("Netflix", 44_900))
            )
        )
        val viewModel = started()

        assertEquals(DetailNudge.Deleting(6), viewModel.nudge)
    }

    @Test
    fun creatingAnyPagoFijoClosesIt() = runTest {
        publish(expenses = listOf(lunch, netflix(110, "2026-09-05"), netflix(111, "2026-10-05")))
        val viewModel = started()
        DoneNudges(corrections.nudgesDone, guidance, backgroundScope)

        corrections.scheduleSaved(
            CorrectionNotice.ScheduleSaved("Gimnasio", null, null, income = false)
        )

        assertNull(viewModel.nudge)
        runCurrent()
        assertTrue(Nudge.FixedPayment in guidance.stored.memory.seen)
    }

    @Test
    fun anIncomeScheduleIsNotAPagoFijo() = runTest {
        publish(expenses = listOf(lunch, netflix(110, "2026-09-05"), netflix(111, "2026-10-05")))
        val viewModel = started()

        corrections.scheduleSaved(
            CorrectionNotice.ScheduleSaved("Sueldo", null, null, income = true)
        )

        assertEquals(DetailNudge.FixedPayment, viewModel.nudge)
    }

    @Test
    fun theMonthsBeforeUnreadOrReadInPartNeverPointAtAPagoFijo() = runTest {
        publish(expenses = listOf(lunch, netflix(110, "2026-09-05"), netflix(111, "2026-10-05")))
        reads.rangeFailure = StorageFailure.Unavailable("SQLiteException")
        assertEquals(DetailNudge.Deleting(6), started().nudge)

        reads.rangeFailure = null
        reads.rangeLeftSomeOut = true
        assertEquals(DetailNudge.Deleting(6), started().nudge)
    }

    @Test
    fun schedulesThatCannotBeReadNeverPointAtAPagoFijo() = runTest {
        publish(expenses = listOf(lunch, netflix(110, "2026-09-05"), netflix(111, "2026-10-05")))
        schedules.state.value =
            ReadState.Failed(StorageFailure.Unavailable("SQLiteException"), null)
        val viewModel = started()

        assertEquals(DetailNudge.Deleting(6), viewModel.nudge)
    }

    // The shared rules

    @Test
    fun olderFiguresCarryNoNudge() = runTest {
        publish()
        val viewModel = started()
        snapshots.of(november).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException")),
            (snapshots.of(november).value as ReadState.Ready).value
        )

        assertTrue((viewModel.state.value as CategoryDetailState.Ready).stale)
        assertNull(viewModel.nudge)
    }

    @Test
    fun guidanceThatCannotBeReadShowsNoNudge() = runTest {
        publish()
        guidance.failReads()
        val viewModel = started()

        assertNull(viewModel.nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun noNewUserNudgeBeforeSelloCheckedWhoWasAlreadyUsingIt() = runTest {
        publish(october = october(500_000, 420_000))
        guidance = HandGuidance(Guidance(existingUseChecked = false))
        val viewModel = started()

        assertNull(viewModel.nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun peopleAlreadyUsingSelloSeeNone() = runTest {
        publish(october = october(500_000, 420_000))
        guidance.rememberExistingUse(wasUsingSello = true)
        guidance.writes.clear()
        val viewModel = started()

        assertNull(viewModel.nudge)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun anotherNudgeShownTodayKeepsTheDay() = runTest {
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, nov8)))
        publish()
        val viewModel = started()

        assertNull(viewModel.nudge)
    }

    @Test
    fun itShowsForOneDayOnly() = runTest {
        // Shown yesterday: gone today, and kept as seen with today's showing of another.
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.Deleting, nov8.minusDays(1))))
        publish(october = october(500_000, 420_000))
        val viewModel = started()

        assertEquals(DetailNudge.Carry(october, surplus = true), viewModel.nudge)
        assertEquals(
            setOf(Nudge.Deleting),
            guidance.stored.memory.seen
        )
        clock.goTo("2026-11-09")
        assertNull(viewModel.nudge)
    }
}
