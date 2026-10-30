package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CategoryComparison
import com.software.sello.domain.model.CategoryHistoryMonth
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Money
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.testing.testMoney
import java.math.BigInteger
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryInsightsRenderModelTest {
    private val render = CategoryInsightsRenderModel(testMoney())
    private val food = categoryIn(1, "Alimentación", 900_000)

    private fun shown(tenths: String?) = render.render(
        CategoryInsights(
            monthOf("2026-10-23", listOf(food), emptyList()),
            food.category.id,
            null,
            tenths?.let { CategoryComparison(YearMonth.of(2026, 9), BigInteger(it)) },
            23, null, null, null, null, emptyList(), null, emptyList()
        )
    )

    @Test
    fun theComparisonRetainsItsExactDirectionAndSignedDisplay() {
        val higher = shown("67")
        assertEquals("+6,7 %", higher.comparison)
        assertEquals(DetailComparisonDirection.Higher, higher.comparisonDirection)
        assertEquals(YearMonth.of(2026, 9), higher.comparisonMonth)
        val lower = shown("-67")
        assertEquals("−6,7 %", lower.comparison)
        assertEquals(DetailComparisonDirection.Lower, lower.comparisonDirection)
        val equal = shown("0")
        assertEquals("0,0 %", equal.comparison)
        assertEquals(DetailComparisonDirection.Equal, equal.comparisonDirection)
    }

    @Test
    fun anUnknownBaselineDoesNotBecomeAZeroComparison() {
        val unknown = shown(null)
        assertNull(unknown.comparison)
        assertNull(unknown.comparisonMonth)
    }

    private fun withHistory(limit: Long? = 200_000): CategoryInsights = CategoryInsights(
        monthOf(
            "2026-10-22",
            listOf(categoryIn(1, "Alimentación", limit), categoryIn(2, "Otra", 900_000)),
            listOf(
                storedExpense(10, 1, "2026-10-01", 100_000),
                storedExpense(11, 2, "2026-10-01", 30_000)
            )
        ),
        food.category.id, null, null, 22, null, null, null, null, emptyList(),
        Money.cop(10_000),
        listOf(
            CategoryHistoryMonth(YearMonth.of(2026, 8), Money.cop(20_000), Money.cop(200_000)),
            CategoryHistoryMonth(YearMonth.of(2026, 9), Money.cop(0), Money.cop(200_000))
        )
    )

    @Test
    fun theCurrentChartPointUsesTheLiveCategoryLimitWithoutRewritingCompletedMonths() {
        val before = render.render(withHistory())
        assertEquals(YearMonth.of(2026, 10), before.history.last().month)
        assertEquals(200_000L, before.history.last().limitValue)
        assertEquals(100_000L, before.history.last().value)
        assertEquals(true, before.history.last().current)
        assertEquals(2, before.history.count { !it.current })
        val after = render.render(withHistory(500_000))
        assertEquals(listOf(200_000L, 200_000L, 500_000L), after.history.map { it.limitValue })
        assertEquals(before.history.dropLast(1), after.history.dropLast(1))
        assertEquals("500.000", after.history.last().limit!!.digits)
        assertEquals("10.000", after.historicalAverage!!.digits)
    }

    @Test
    fun aCurrentUnlimitedOrZeroLimitIsNotReplacedWithThePreviousMonthsLimit() {
        val unlimited = render.render(withHistory(null)).history.last()
        assertEquals(YearMonth.of(2026, 10), unlimited.month)
        assertNull(unlimited.limit)
        assertNull(unlimited.limitValue)
        val zero = render.render(withHistory(0)).history.last()
        assertEquals(0L, zero.limitValue)
        assertEquals("0", zero.limit!!.digits)
    }

    @Test
    fun aClosedSelectedMonthDoesNotGainAnExtraCurrentPoint() {
        val input = withHistory()
        val closed = input.copy(budget = input.budget.copy(month = YearMonth.of(2026, 9)))
        val shown = render.render(closed)
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            shown.history.map {
                it.month
            }
        )
        assertEquals("10.000", shown.historicalAverage!!.digits)
        assertEquals(false, shown.history.any { it.current })
    }

    @Test
    fun aCompletedMonthThatCarriedIsDrawnAgainstBasePlusCarryWithItsParts() {
        // SC09's feb, seen from mar: base 100.000, carry +80.000, capacity 180.000, 30.000
        // spent: within, never over the 100.000 base alone.
        val feb = YearMonth.of(2027, 2)
        val carried = withHistory().copy(
            history = listOf(
                CategoryHistoryMonth(
                    feb,
                    Money.cop(30_000),
                    Money.cop(180_000),
                    CarryBreakdown(
                        Money.cop(100_000),
                        Money.cop(80_000),
                        Money.cop(180_000),
                        YearMonth.of(2027, 1)
                    )
                )
            )
        )

        val month = render.render(carried).history.first()

        assertEquals(180_000L, month.limitValue)
        assertEquals("180.000", month.limit!!.digits)
        assertEquals(
            listOf("100.000", "80.000", "180.000"),
            listOf(
                month.carry!!.base.digits,
                month.carry!!.carry.digits,
                month.carry!!.capacity.digits
            )
        )
        assertEquals(MoneySign.Plus, month.carry!!.carry.sign)
    }
}
