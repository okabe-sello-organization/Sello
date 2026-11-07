package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.RepeatedNote
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryInsightsPolicyTest {
    private val month = YearMonth.of(2026, 10)
    private val stamp = Instant.parse("2026-10-22T12:00:00Z")
    private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val id = CategoryId.of(uuid(1)).valueOrFail()
    private fun category(limit: Long? = 200_000) = CategoryInMonth(
        Category(
            id,
            CategoryName.of("Comida").valueOrFail(),
            IconKey.of("restaurant").valueOrFail(),
            false,
            1,
            stamp,
            stamp
        ),
        MonthBudgetState.Limited(
            limit?.let {
                BudgetLimit.finite(Money.cop(it)).valueOrFail()
            } ?: BudgetLimit.Unlimited,
            false
        )
    )
    private fun expense(
        n: Int,
        day: String,
        pesos: Long,
        categoryId: CategoryId = id,
        note: String? = null
    ) = Expense(
        ExpenseId.of(uuid(100 + n)).valueOrFail(), categoryId, LocalDate.parse(day), n.toLong(),
        TransactionAmount.of(
            Money.cop(pesos)
        ).valueOrFail(),
        Note.of(note).valueOrFail(), 1, stamp, stamp
    )
    private fun read(
        expenses: List<Expense> = emptyList(),
        today: String = "2026-10-22",
        selected: YearMonth = month,
        configuredFrom: YearMonth = YearMonth.of(2026, 8),
        limit: Long? = 200_000
    ) = CategoryInsightsPolicy.snapshot(
        id,
        selected,
        FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota")),
        3,
        41,
        CategoryInsightsPolicy.monthsEndingIn(selected).map { period ->
            CategoryHistoryInput(
                period,
                category(limit).let {
                    if (period <
                        configuredFrom
                    ) {
                        it.copy(state = MonthBudgetState.Unconfigured)
                    } else {
                        it
                    }
                },
                expenses
            )
        }
    )

    @Test
    fun approvedOctoberExampleReservesOnlySevenOfTheTenRemainingDays() {
        val result = read(listOf(expense(1, "2026-10-01", 100_000))).valueOrFail()
        assertEquals(Money.cop(100_000), result.budget.categories.single().budget!!.remaining)
        assertEquals(Money.cop(10_000), result.allowance!!.amount)
        assertEquals(10, result.allowance.availableDays)
        assertEquals(listOf(100_000L, 0L, 0L, 0L), result.weeks.map { it.spent.minorUnits })
        assertEquals(Money.cop(70_000), result.weeks.last().remainingAllowance)
        // Three completed weeks: 100,000 / 3 = 33,333.333…, rounded once.
        assertEquals(Money.cop(33_333), result.weeklyAverage)
    }

    @Test
    fun daysAndTheFinalShortWeekIncludeTodayWithoutExceedingTheRemainder() {
        val last = read(
            listOf(expense(1, "2026-10-01", 195_000)),
            today = "2026-10-31"
        ).valueOrFail()
        assertEquals(Money.cop(5_000), last.weeks.last().remainingAllowance)
        assertEquals(5, last.weeks.size)
        assertEquals(Money.cop(48_750), last.weeklyAverage)
        val early = read(today = "2026-10-01").valueOrFail()
        assertNull(early.weeklyAverage)
        assertEquals(Money.cop(45_161), early.weeks.single().remainingAllowance)
        assertEquals(Money.cop(6_450), early.allowance!!.amount)
    }

    @Test
    fun leapDayIsAOneDayFinalBucketAndAverageExpensesRoundHalfUpOnce() {
        val leapDay = read(
            listOf(expense(1, "2024-02-01", 195_000)),
            today = "2024-02-29",
            selected = YearMonth.of(2024, 2),
            configuredFrom = YearMonth.of(2024, 1)
        ).valueOrFail()
        assertEquals(LocalDate.of(2024, 2, 29), leapDay.weeks.last().from)
        assertEquals(LocalDate.of(2024, 2, 29), leapDay.weeks.last().until)
        assertEquals(Money.cop(5_000), leapDay.weeks.last().remainingAllowance)
        assertEquals(
            Money.cop(1_001),
            read(
                listOf(expense(1, "2026-10-01", 1_000), expense(2, "2026-10-02", 1_001))
            ).valueOrFail().averageExpense
        )
    }

    @Test
    fun historicalAverageIncludesKnownZeroMonthsAndExcludesUnconfiguredMonths() {
        val result = read(listOf(expense(1, "2026-08-03", 20_000))).valueOrFail()
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            result.history.map {
                it.month
            }
        )
        assertEquals(Money.cop(10_000), result.historicalAverage)
        assertNull(read(configuredFrom = YearMonth.of(2026, 9)).valueOrFail().historicalAverage)
    }

    @Test
    fun actualStatisticsExcludeFutureDatesAndOtherCategoriesAndUseDeterministicTies() {
        val result = read(
            listOf(
                expense(1, "2026-10-01", 10_001), // Thursday
                expense(2, "2026-10-08", 20_000), // Thursday
                expense(3, "2026-10-09", 20_000), // Friday, newest largest
                expense(4, "2026-10-23", 999_999),
                expense(5, "2026-10-01", 900_000, CategoryId.of(uuid(2)).valueOrFail())
            )
        ).valueOrFail()
        assertEquals(Money.cop(50_001), result.budget.spent)
        assertEquals(3, result.budget.categories.single().expenseCount)
        assertEquals(Money.cop(16_667), result.averageExpense)
        assertEquals(ExpenseId.of(uuid(103)).valueOrFail(), result.largestExpense!!.id)
        assertEquals(DayOfWeek.THURSDAY, result.mostExpensiveWeekday)
        assertEquals(22, result.elapsedDays)
    }

    @Test
    fun repeatsCountTheSelectedMonthOnlyAndThisCategoryOnly() {
        val other = CategoryId.of(uuid(2)).valueOrFail()
        val snapshot = read(
            listOf(
                expense(1, "2026-10-02", 20_000, note = "Rappi"),
                expense(2, "2026-10-25", 20_000, note = "Rappi"),
                expense(3, "2026-09-15", 20_000, note = "Rappi"),
                expense(4, "2026-10-03", 900_000, other, "Rappi")
            )
        ).valueOrFail()
        assertEquals(
            listOf(RepeatedNote(Note.of("Rappi").valueOrFail()!!, 2, Money.cop(40_000))),
            snapshot.repeats
        )
    }

    @Test
    fun previousMonthComparisonClampsShortMonthsAndPastMonthsUseWholePeriods() {
        val march = read(
            listOf(expense(1, "2024-02-29", 20_000), expense(2, "2024-03-31", 30_000)),
            today = "2024-03-31",
            selected = YearMonth.of(2024, 3),
            configuredFrom = YearMonth.of(2024, 1)
        ).valueOrFail()
        assertEquals(500.toBigInteger(), march.comparison!!.tenthsPercent)
        val closed = read(
            listOf(expense(1, "2026-09-30", 10_000)),
            selected = YearMonth.of(2026, 9)
        ).valueOrFail()
        assertNull(closed.allowance)
        assertEquals(30, closed.elapsedDays)
        assertEquals(false, closed.weeks.last().current)
        assertEquals(YearMonth.of(2026, 9), closed.history.last().month)
    }

    @Test
    fun emptyUnlimitedAndOverspentBudgetsDoNotInventAveragesOrAllowances() {
        val empty = read().valueOrFail()
        assertNull(empty.averageExpense)
        assertNull(empty.largestExpense)
        assertNull(empty.mostExpensiveWeekday)
        assertNull(empty.comparison)
        assertNull(read(limit = null).valueOrFail().allowance)
        val over = read(listOf(expense(1, "2026-10-01", 250_000))).valueOrFail()
        assertEquals(Money.cop(-50_000), over.budget.categories.single().budget!!.remaining)
        assertEquals(Money.cop(0), over.weeks.last().remainingAllowance)
    }

    @Test
    fun largeAmountsRemainExactAndFutureMonthsAndNegativeYearsAreExcluded() {
        // Each expense obeys the transaction cap. Monthly totals can exceed it.
        val huge =
            listOf(
                expense(1, "2026-08-01", 999_999_999_999),
                expense(2, "2026-09-01", 999_999_999_999)
            )
        assertEquals(Money.cop(999_999_999_999), read(huge).valueOrFail().historicalAverage)
        assertEquals(
            SnapshotFailure.FutureMonth(month),
            read(selected = month.plusMonths(1)).errorOrFail()
        )
        assertEquals(
            listOf(YearMonth.of(0, 1)),
            CategoryInsightsPolicy.monthsEndingIn(YearMonth.of(0, 1))
        )
    }
}
