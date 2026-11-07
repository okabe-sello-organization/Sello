package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.YearMonthStatus
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.goals.HandGoalReads
import com.software.sello.feature.resumen.yearSnapshot
import com.software.sello.testing.HandYearSnapshots
import com.software.sello.testing.pesos
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The year's big moment: what the plate counts, read only while it is open. */
@OptIn(ExperimentalCoroutinesApi::class)
class YearCloseViewModelTest {
    private val years = HandYearSnapshots()
    private val goals = HandGoalReads()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun TestScope.model(saved: SavedStateHandle = SavedStateHandle()): YearCloseViewModel {
        val model = YearCloseViewModel(saved, years, goals)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        return model
    }

    private val YearCloseViewModel.ready get() = state.value as YearCloseState.Ready

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** A goal; [completedOn] is the day it was marked as reached, if it was. */
    private fun goal(number: Int, completedOn: String?): GoalSummary {
        val history = GoalHistory(
            Goal(
                GoalId.of("00000000-0000-4000-9000-%012d".format(number)).value(),
                GoalName.of("Meta $number").value(),
                IconKey.of("flight").value(),
                null,
                completedOn?.let(LocalDate::parse),
                1,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf(GoalTargetChange(pesos(1_000_000), 0, LocalDate.of(2025, 6, 1)))
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun board(vararg goals: GoalSummary) =
        ReadState.Ready(GoalBoard(1, 1, goals.toList(), emptySet(), emptyList()))

    /**
     * The fixture's 2026, finished: 180 expenses on 150 days, every month with a limit,
     * March and July over.
     */
    private fun closed2026(): YearSnapshot = yearSnapshot(asOf = "2027-01-02")

    /** The same year with a limit only from January to [limitedThrough], March over. */
    private fun limitedUntil(limitedThrough: Int): YearSnapshot {
        val year = closed2026()
        return year.copy(
            months = year.months.map { line ->
                val number = line.month.monthValue
                line.copy(
                    status = YearMonthStatus.Finished(
                        when {
                            number > limitedThrough -> null
                            number == 3 -> PastVerdict.Over
                            else -> PastVerdict.WithinLimit
                        }
                    )
                )
            }
        )
    }

    @Test
    fun closedItReadsNothing() = runTest {
        val model = model()

        assertEquals(YearCloseState.Closed, model.state.value)
        assertEquals(emptyList<Int>(), years.observed)
        assertEquals(0, goals.started)
    }

    @Test
    fun theYearsCountsAreHandCounted() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        assertEquals(YearCloseState.Loading(2026), model.state.value)

        years.of(2026).value = ReadState.Ready(closed2026())
        goals.board.value = board(
            goal(1, completedOn = "2026-03-10"),
            goal(2, completedOn = "2026-11-30"),
            // Reached the year before, and not yet reached: neither counts.
            goal(3, completedOn = "2025-12-31"),
            goal(4, completedOn = null)
        )

        val ready = model.ready
        assertEquals(2026, ready.year)
        assertEquals(180, ready.expenses)
        assertEquals(150, ready.recordedDays)
        // Twelve months with a limit, March and July over: 10 of 12.
        assertEquals(10, ready.withinMonths)
        assertEquals(12, ready.limitedMonths)
        assertEquals(2, ready.goalsReached)
        assertEquals((1..12).map { YearMonth.of(2026, it) }, ready.months.map { it.month })
        assertEquals(
            listOf(3, 7),
            ready.months.filter { it.mark == YearCloseMark.Over }.map { it.month.monthValue }
        )
    }

    @Test
    fun aYearWithoutGoalsReachedCountsNone() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        years.of(2026).value = ReadState.Ready(closed2026())
        goals.board.value = board()

        assertEquals(0, model.ready.goalsReached)
    }

    @Test
    fun aYearWhereNoMonthHadALimitCountsNoMonths() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        years.of(2026).value = ReadState.Ready(limitedUntil(0))
        goals.board.value = board()

        assertEquals(0, model.ready.limitedMonths)
        assertEquals(0, model.ready.withinMonths)
        assertEquals(true, model.ready.months.all { it.mark == YearCloseMark.NoLimit })
    }

    @Test
    fun onlyMonthsWithALimitAreCounted() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        // January to June had a limit, March over; July to December had none: 5 of 6.
        years.of(2026).value = ReadState.Ready(limitedUntil(6))
        goals.board.value = board()

        assertEquals(5, model.ready.withinMonths)
        assertEquals(6, model.ready.limitedMonths)
        assertEquals(YearCloseMark.NoLimit, model.ready.months[6].mark)
    }

    @Test
    fun aFailedYearReadShowsNoCountNotEvenAnOlderOne() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        goals.board.value = board()
        years.of(2026).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException")),
            lastGood = closed2026()
        )

        assertEquals(YearCloseState.Failed(2026), model.state.value)
    }

    @Test
    fun aFailedGoalReadShowsNoCount() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        years.of(2026).value = ReadState.Ready(closed2026())
        goals.board.value = ReadState.Failed(StorageFailure.Unavailable("SQLiteException"), null)

        assertEquals(YearCloseState.Failed(2026), model.state.value)
    }

    @Test
    fun doneClosesIt() = runTest {
        val model = model()
        model.onAction(YearCloseAction.Open(2026))
        years.of(2026).value = ReadState.Ready(closed2026())
        goals.board.value = board()

        model.onAction(YearCloseAction.Done)

        assertEquals(YearCloseState.Closed, model.state.value)
    }

    @Test
    fun anOpenMomentComesBackAfterProcessDeath() = runTest {
        val saved = SavedStateHandle()
        model(saved).onAction(YearCloseAction.Open(2026))
        years.of(2026).value = ReadState.Ready(closed2026())
        goals.board.value = board()

        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))

        assertEquals(2026, restored.ready.year)
    }
}
