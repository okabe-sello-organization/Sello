package com.software.sello.feature.resumen

import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DailySpending
import com.software.sello.domain.model.LimitAdjustment
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.NormalMonth
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SpendingComparison
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.domain.model.SpendingMonth
import com.software.sello.domain.model.WeekdayPattern
import com.software.sello.domain.model.WeekdaySpending
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.testing.testMoney
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun resumenMoney() = testMoney()

internal fun resumenSnapshot(spent: Long = 30_000, income: Long = 150_000): ResumenSnapshot {
    val month = YearMonth.of(2026, 10)
    return ResumenSnapshot(
        MonthlySnapshot(
            month, LocalDate.of(2026, 10, 3), ZoneId.of("America/Bogota"), 1, 7,
            emptyList(), Money.cop(spent), OverallBudget.NoLimit, 0
        ),
        Money.cop(income), Money.cop(income - spent), Money.cop(100_000),
        Money.cop(income - 100_000), PastVerdict.WithinLimit,
        listOf(
            DailySpending(month.atDay(1), Money.cop(10_000), Money.cop(10_000)),
            DailySpending(month.atDay(2), Money.cop(15_000), Money.cop(25_000)),
            DailySpending(month.atDay(3), Money.cop(5_000), Money.cop(spent))
        ),
        listOf(SpendingMonth(month, Money.cop(spent), Money.cop(100_000), null, 3)), 0,
        incomeCount = 1
    )
}

class ResumenRenderModelTest {
    private val render = ResumenRenderModel(resumenMoney())

    /** Rendered in October 2026, the fixture's month. */
    private fun ResumenRenderModel.render(state: ResumenSnapshotState) =
        render(state, YearMonth.of(2026, 10))

    @Test
    fun cashFlowAndPlanningCapacityAreDifferentExactAmounts() {
        val state = render.render(ReadState.Ready(resumenSnapshot()))
        assertTrue(state is ResumenState.Ready)
        state as ResumenState.Ready
        assertEquals("120.000 pesos", state.netFlow.spoken)
        assertFalse(state.stale)
    }

    @Test
    fun aSingleMonthHasTooLittleHistoryAndTheDateIsWrittenAsInASentence() {
        val state = render.render(ReadState.Ready(resumenSnapshot()))
        assertTrue(state is ResumenState.Ready)
        state as ResumenState.Ready
        assertTrue(state.insufficientHistory)
        assertEquals("3 de octubre", state.asOf)
    }

    @Test
    fun largeAndNegativeFiguresStayExact() {
        val snapshot = resumenSnapshot().copy(
            netFlow = Money.cop(-9_007_199_254_740_993),
            history = listOf(
                SpendingMonth(
                    YearMonth.of(2026, 10),
                    Money.cop(9_007_199_254_740_993),
                    null,
                    null,
                    1
                )
            )
        )
        val state = render.render(ReadState.Ready(snapshot))
        assertTrue(state is ResumenState.Ready)
        state as ResumenState.Ready
        assertEquals("menos 9.007.199.254.740.993 pesos", state.netFlow.spoken)
        assertEquals(9_007_199_254_740_993L, state.history.single().chart.value)
        assertEquals("9.007.199.254.740.993 pesos", state.history.single().amount.spoken)
    }

    @Test
    fun readFailureIsNotAnEmptyMonthAndLastGoodIsExplicitlyStale() {
        assertEquals(
            ResumenState.Failed,
            render.render(
                ReadState.Failed(SnapshotFailure.Unrepresentable, null)
            )
        )
        val state = render.render(
            ReadState.Failed(SnapshotFailure.Unrepresentable, resumenSnapshot())
        )
        assertTrue(state is ResumenState.Ready)
        assertTrue((state as ResumenState.Ready).stale)
        assertEquals("3 de octubre", state.asOf)
    }

    @Test
    fun theRecordedUntilDayShowsItsYearOnlyWhenItIsFromAnotherYear() {
        val ready = ReadState.Ready(resumenSnapshot())

        assertEquals("3 de octubre", (render.render(ready) as ResumenState.Ready).asOf)
        assertEquals(
            "3 de octubre de 2026",
            (render.render(ready, YearMonth.of(2027, 1)) as ResumenState.Ready).asOf
        )
    }

    // --- SELLO-123: where the income went ---

    private fun incomeOf(snapshot: ResumenSnapshot) =
        (render.render(ReadState.Ready(snapshot)) as ResumenState.Ready).incomeSlip

    @Test
    fun theIncomeSlipCountsTheIncomesAndDrawsWhatWasSpentOfThem() {
        val slip =
            incomeOf(resumenSnapshot(spent = 400_000, income = 2_000_000).copy(incomeCount = 3))

        assertEquals(3, slip.count)
        // 400.000 of 2.000.000: a fifth of the bar.
        assertEquals(0.2f, slip.spentShare, 0.0001f)
        assertFalse(slip.overspent)
    }

    @Test
    fun spendingAboveTheIncomeFillsTheBarAndSaysSo() {
        val slip = incomeOf(resumenSnapshot(spent = 1_120_000, income = 1_000_000))

        assertEquals(1f, slip.spentShare)
        assertTrue(slip.overspent)
    }

    @Test
    fun withNoIncomeNothingIsDrawnAsSpentOfIt() {
        val slip = incomeOf(resumenSnapshot(spent = 30_000, income = 0).copy(incomeCount = 0))

        assertEquals(0, slip.count)
        assertEquals(0f, slip.spentShare)
    }

    // --- v2: the month in progress, the limit line and the ranking ---

    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun theMonthInProgressIsAlwaysMarkedCurrentEvenOverItsLimitAndCarriesTheLimitLine() {
        // September ended over its 100.000; October is over its 100.000 so far.
        val snapshot = resumenSnapshot().copy(
            history = listOf(
                SpendingMonth(september, Money.cop(120_000), Money.cop(100_000), null, 4),
                SpendingMonth(october, Money.cop(130_000), Money.cop(100_000), null, 3)
            )
        )

        val state = render.render(ReadState.Ready(snapshot)) as ResumenState.Ready

        assertEquals(listOf(false, true), state.history.map { it.current })
        assertEquals(
            listOf(ChartPeriod.Over, ChartPeriod.Current),
            state.history.map { it.chart.period }
        )
        assertEquals(
            100_000L to "100.000 pesos",
            state.monthLimit?.let {
                it.value to
                    it.amount.spoken
            }
        )
    }

    @Test
    fun aClosedMonthIsNotDrawnAsInProgressAndAMonthWithoutLimitHasNoLine() {
        val snapshot = resumenSnapshot().copy(
            budget = resumenSnapshot().budget.copy(month = september),
            history = listOf(SpendingMonth(september, Money.cop(80_000), null, null, 2))
        )

        val state = render.render(ReadState.Ready(snapshot)) as ResumenState.Ready

        assertEquals(listOf(false), state.history.map { it.current })
        assertEquals(null, state.monthLimit)
    }

    @Test
    fun categoriesAreRankedByWhatWasSpentWithTheirShareOfTheMonthAndAddUpToIt() {
        val budget = monthOf(
            "2026-10-03",
            listOf(
                categoryIn(1, "Comida", 300_000, icon = "restaurant"),
                categoryIn(2, "Casa", null, icon = "home"),
                categoryIn(3, "Ocio", 50_000, icon = "movie")
            ),
            listOf(
                storedExpense(1, 1, "2026-10-01", 15_000),
                storedExpense(2, 2, "2026-10-02", 45_000),
                storedExpense(3, 1, "2026-10-03", 15_000)
            )
        )
        val snapshot = resumenSnapshot().copy(budget = budget)

        val state = render.render(ReadState.Ready(snapshot)) as ResumenState.Ready

        // Spent 75.000: Casa 45.000 (3/5), Comida 30.000 (2/5); Ocio spent nothing.
        assertEquals(
            listOf("Casa" to "45.000 pesos", "Comida" to "30.000 pesos"),
            state.categories.map { it.name to it.amount.spoken }
        )
        assertEquals(listOf("home", "restaurant"), state.categories.map { it.iconKey })
        assertEquals(0.6f, state.categories[0].share, 0.0001f)
        assertEquals(0.4f, state.categories[1].share, 0.0001f)
        assertEquals("75.000 pesos", state.spent.spoken)
    }

    // --- v2: the comparison, a normal month, weekdays and the suggestion ---

    private fun ready(snapshot: ResumenSnapshot) =
        render.render(ReadState.Ready(snapshot)) as ResumenState.Ready

    @Test
    fun theMonthInProgressIsComparedWithTheSameDaysOfSeptember() {
        val snapshot = resumenSnapshot(spent = 400_000).copy(
            comparison = SpendingComparison(
                september,
                Money.cop(400_000),
                Money.cop(450_000),
                3,
                3,
                Money.cop(50_000),
                SpendingDirection.Less,
                BigInteger.valueOf(111)
            )
        )

        val hero = ready(snapshot).hero

        assertTrue(hero.inProgress)
        assertEquals("octubre" to 3, hero.monthName to hero.throughDay)
        val comparison = hero.comparison!!
        assertEquals("septiembre", comparison.previousName)
        assertEquals("50.000 pesos", comparison.difference.spoken)
        assertEquals("11,1", comparison.percent)
        assertEquals(3 to 3, comparison.throughDay to comparison.previousThroughDay)
        assertEquals(
            listOf("Octubre" to "400.000", "Septiembre" to "450.000"),
            comparison.bars.map { it.label to it.amount.digits }
        )
        // 400.000 of 450.000 on the same scale; the larger fills the bar.
        assertEquals(400_000f / 450_000f, comparison.bars[0].fraction, 0.0001f)
        assertEquals(1f, comparison.bars[1].fraction, 0.0001f)
        assertEquals(listOf(true, false), comparison.bars.map { it.current })
    }

    @Test
    fun aFinishedMonthWithNothingToCompareShowsWhatItSpentAlone() {
        val spent = resumenSnapshot(spent = 80_000)
        val snapshot = spent.copy(budget = spent.budget.copy(month = september))

        val hero = ready(snapshot).hero

        assertFalse(hero.inProgress)
        assertEquals("septiembre" to 30, hero.monthName to hero.throughDay)
        assertEquals("80.000 pesos", hero.spent.spoken)
        assertNull(hero.comparison)
    }

    @Test
    fun aNormalMonthIsShownOnlyOnceThereIsHistory() {
        val normal = NormalMonth(Money.cop(778_000), 5)
        val alone = resumenSnapshot().copy(normalMonth = normal)
        val history = alone.copy(
            history = listOf(
                SpendingMonth(september, Money.cop(900_000), null, null, 4),
                SpendingMonth(october, Money.cop(30_000), null, null, 3)
            )
        )

        assertNull(ready(alone).normalMonth)
        assertEquals(
            "778.000 pesos" to 5,
            ready(history).normalMonth?.let {
                it.amount.spoken to
                    it.months
            }
        )
    }

    @Test
    fun weekdaysAreMondayFirstWithTheBusiestNamedAndExact() {
        val averages = listOf(18_200L, 21_500L, 19_800L, 24_100L, 31_400L, 42_700L, 27_300L)
        val snapshot = resumenSnapshot().copy(
            weekdays = WeekdayPattern(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 10, 3),
                DayOfWeek.entries.zip(averages) { day, pesos ->
                    WeekdaySpending(day, Money.cop(pesos))
                },
                DayOfWeek.SATURDAY,
                lastSixMonths = true
            )
        )

        val weekdays = ready(snapshot).weekdays!!

        assertEquals(
            listOf("L", "M", "X", "J", "V", "S", "D"),
            weekdays.points.map {
                it.chart.axisLabel
            }
        )
        assertEquals(averages, weekdays.points.map { it.chart.value })
        assertEquals("sábados, 42.700 pesos", weekdays.points[5].chart.spoken)
        assertEquals(
            listOf(
                ChartPeriod.Past,
                ChartPeriod.Past,
                ChartPeriod.Past,
                ChartPeriod.Past,
                ChartPeriod.Past,
                ChartPeriod.Current,
                ChartPeriod.Past
            ),
            weekdays.points.map { it.chart.period }
        )
        assertEquals(
            "sábados" to "42.700 pesos",
            weekdays.busiest to weekdays.busiestAverage?.spoken
        )
        assertEquals("1 de julio" to true, weekdays.from to weekdays.lastSixMonths)
    }

    @Test
    fun withNoBusiestDayNoneIsNamedOrHighlighted() {
        val snapshot = resumenSnapshot().copy(
            weekdays = WeekdayPattern(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 9),
                DayOfWeek.entries.map { WeekdaySpending(it, Money.cop(1_000)) },
                null
            )
        )

        val weekdays = ready(snapshot).weekdays!!

        assertNull(weekdays.busiest)
        assertNull(weekdays.busiestAverage)
        assertTrue(weekdays.points.all { it.chart.period == ChartPeriod.Past })
    }

    @Test
    fun theSuggestionNamesTheCategoryItsMonthsAndBothAmounts() {
        val hogar = (CategoryId.of("00000000-0000-4000-8000-000000000002") as Outcome.Success).value
        val snapshot = resumenSnapshot().copy(
            suggestion = LimitAdjustment(
                hogar,
                (CategoryName.of("Hogar") as Outcome.Success).value,
                listOf(YearMonth.of(2026, 8), september),
                Money.cop(230_000),
                Money.cop(200_000)
            )
        )

        assertEquals(
            ResumenSuggestion(
                hogar.value,
                "Hogar",
                listOf("agosto", "septiembre"),
                resumenMoney().format(Money.cop(230_000)),
                resumenMoney().format(Money.cop(200_000))
            ),
            ready(snapshot).suggestion
        )
    }
}
