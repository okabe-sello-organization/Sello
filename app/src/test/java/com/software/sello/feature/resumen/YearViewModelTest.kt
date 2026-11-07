package com.software.sello.feature.resumen

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.HandYearSnapshots
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMoney
import com.software.sello.testing.testMonthNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModelTest {
    private val reads = HandYearSnapshots()
    private val clock = HandFinancialClock("2026-11-06")

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(saved: SavedStateHandle = SavedStateHandle(mapOf("year" to 2026))) =
        YearViewModel(saved, reads, testMoney(), testMonthNames(), testDateLabels(), clock)

    private fun TestScope.watch(model: YearViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    private val YearViewModel.ready get() = state.value as YearState.Ready

    @Test
    fun opensOnTheRouteYearAndShowsTheYearAgainstTheOneBefore() = runTest {
        val model = model()
        watch(model)
        assertEquals(YearState.Loading(2026), model.state.value)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())

        val hero = model.ready.hero
        assertEquals(true, hero.inProgress)
        assertEquals("14.230.000", hero.spent.digits)
        assertEquals("6 de noviembre", hero.through)
        val comparison = hero.comparison!!
        assertEquals(2025, comparison.previousYear)
        assertEquals("1.120.000", comparison.difference.digits)
        assertEquals(SpendingDirection.Less, comparison.direction)
        assertEquals("7,3", comparison.percent)
        assertNull(comparison.previousThrough)
        assertEquals(listOf("2026", "2025"), comparison.bars.map { it.label })
        assertEquals(
            listOf("14.230.000", "15.350.000"),
            comparison.bars.map { it.amount.digits }
        )
    }

    @Test
    fun monthsCarryTheirMarksAndTheMonthInProgressIsBrandInk() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())

        val ready = model.ready
        assertEquals(12, ready.months.size)
        assertEquals(
            listOf(
                YearMark.Within, YearMark.Within, YearMark.Over, YearMark.Within,
                YearMark.Within, YearMark.Within, YearMark.Over, YearMark.Within,
                YearMark.Within, YearMark.Within, YearMark.None, YearMark.None
            ),
            ready.months.map { it.mark }
        )
        assertEquals(ChartPeriod.Current, ready.months[10].chart.period)
        assertEquals(ChartPeriod.Upcoming, ready.months[11].chart.period)
        assertNull(ready.months[11].amount)
        assertEquals(8 to 2, ready.withinMonths to ready.overMonths)
        assertEquals("noviembre", ready.monthInProgress)
        assertEquals("E", ready.months[0].chart.axisLabel)
    }

    @Test
    fun selectingAColumnShowsItsExactValueAndAMonthToComeCannotBe() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())

        model.onAction(YearAction.SelectMonth("2026-03"))
        assertEquals("2026-03", model.ready.selectedMonth)
        assertEquals(
            "1.840.000",
            model.ready.months.first { it.chart.id == "2026-03" }.amount!!.digits
        )
        model.onAction(YearAction.SelectMonth("2026-12"))
        assertEquals("2026-03", model.ready.selectedMonth)
    }

    @Test
    fun aSelectionClearsThreeSecondsAfterTheLastTapAsResumens() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())

        model.onAction(YearAction.SelectMonth("2026-03"))
        advanceTimeBy(2_000)
        // A second tap keeps it and starts the 3 seconds again; it never toggles off.
        model.onAction(YearAction.SelectMonth("2026-03"))
        advanceTimeBy(2_000)
        assertEquals("2026-03", model.ready.selectedMonth)
        advanceTimeBy(1_001)
        assertNull(model.ready.selectedMonth)
    }

    @Test
    fun aRestoredSelectionClearsLikeANewOne() = runTest {
        val model = model(SavedStateHandle(mapOf("year" to 2026, "tuano.month" to "2026-03")))
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())
        assertEquals("2026-03", model.ready.selectedMonth)
        advanceTimeBy(3_001)
        assertNull(model.ready.selectedMonth)
    }

    @Test
    fun loQueEntroIsSigned() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())
        assertEquals("31.500.000", model.ready.income.digits)
        assertEquals("17.270.000", model.ready.netFlow.digits)
        assertEquals(false, model.ready.overspent)

        reads.of(2026).value = ReadState.Ready(
            yearSnapshot().copy(income = Money.cop(1_000_000), netFlow = Money.cop(-500_000))
        )
        assertEquals(MoneySign.Minus to "500.000", model.ready.netFlow.let { it.sign to it.digits })
        assertEquals(true, model.ready.overspent)
    }

    @Test
    fun theTopFiveShowThenEveryCategoryWhenAsked() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())

        assertEquals(
            listOf(46, 27, 10, 8, 4),
            model.ready.shownCategories.map { it.percent }
        )
        assertEquals(9, model.ready.categories.size)
        assertEquals(1f, model.ready.categories.first().bar)
        model.onAction(YearAction.ShowAllCategories)
        assertEquals(9, model.ready.shownCategories.size)
    }

    @Test
    fun movesBetweenTheFirstYearWithRecordsAndTheCurrentOne() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())
        assertEquals(true to false, model.ready.canPrevious to model.ready.canNext)

        model.onAction(YearAction.Next)
        assertEquals(2026, model.state.value.year)
        model.onAction(YearAction.Previous)
        assertEquals(YearState.Loading(2025), model.state.value)
        reads.of(2025).value = ReadState.Ready(yearSnapshot(year = 2025, firstYear = 2025))
        assertEquals(false to true, model.ready.canPrevious to model.ready.canNext)
        assertEquals(false, model.ready.hero.inProgress)
        model.onAction(YearAction.Previous)
        assertEquals(2025, model.state.value.year)
    }

    @Test
    fun theYearShownIsRestoredAfterProcessDeath() = runTest {
        val saved = SavedStateHandle(mapOf("year" to 2026))
        val first = model(saved)
        watch(first)
        reads.of(2026).value = ReadState.Ready(yearSnapshot())
        first.onAction(YearAction.Previous)

        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        watch(restored)
        assertEquals(YearState.Loading(2025), restored.state.value)
        reads.of(2025).value = ReadState.Ready(yearSnapshot(year = 2025, firstYear = 2025))
        assertEquals(2025, restored.ready.year)
    }

    @Test
    fun aFailedReadKeepsTheLastFiguresAndNothingReadIsTheError() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        assertEquals(YearState.Failed(2026), model.state.value)

        reads.of(2026).value = ReadState.Ready(yearSnapshot())
        reads.of(2026).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        assertEquals(true, model.ready.stale)
        assertEquals("14.230.000", model.ready.hero.spent.digits)

        model.onAction(YearAction.Retry)
        assertEquals(2, reads.started)
        assertEquals(true, model.ready.stale)
    }

    @Test
    fun aYearWithNothingRecordedIsEmpty() = runTest {
        val model = model()
        watch(model)
        reads.of(2026).value = ReadState.Ready(yearSnapshot(empty = true))
        assertEquals(true, model.ready.empty)
    }

    @Test
    fun withoutARouteYearItOpensOnTheCurrentYear() = runTest {
        val model = model(SavedStateHandle())
        watch(model)
        assertEquals(YearState.Loading(2026), model.state.value)
    }
}
