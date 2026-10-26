package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumenSnapshotTest {
    @Test
    fun incomePreviewIsExactAndNeverWrapsAnOverflowingMonth() {
        val snapshot = read(incomes = listOf(income(1, 150_000))).valueOrFail()
        assertEquals(
            Money.cop(175_000),
            IncomePreviewPolicy.preview(snapshot, amount(25_000)).valueOrFail()
        )
        assertEquals(
            SnapshotFailure.Unrepresentable,
            IncomePreviewPolicy.preview(
                snapshot.copy(income = Money.cop(Long.MAX_VALUE)),
                amount(1)
            ).errorOrFail()
        )
        assertEquals(Money.cop(150_000), snapshot.income)
    }

    private val october = YearMonth.of(2026, 10)
    private val recorded = Instant.parse("2026-10-09T15:04:05Z")
    private val today = FinancialDay(LocalDate.of(2026, 10, 9), ZoneId.of("America/Bogota"))

    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun category(number: Int, limit: BudgetLimit) = CategoryInMonth(
        Category(
            CategoryId.of(uuid(number)).valueOrFail(),
            CategoryName.of("Categoría $number").valueOrFail(),
            IconKey.of("shopping_cart").valueOrFail(),
            false,
            1,
            recorded,
            recorded
        ),
        MonthBudgetState.Limited(limit, explicit = false)
    )

    private fun limited(number: Int, pesos: Long) =
        category(number, BudgetLimit.finite(Money.cop(pesos)).valueOrFail())

    private fun amount(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).valueOrFail()

    private fun expense(number: Int, pesos: Long, date: String = "2026-10-01") = Expense(
        ExpenseId.of(uuid(100 + number)).valueOrFail(),
        CategoryId.of(uuid(1)).valueOrFail(),
        LocalDate.parse(date),
        number.toLong(),
        amount(pesos),
        null,
        1,
        recorded,
        recorded
    )

    private fun income(number: Int, pesos: Long, date: String = "2026-10-01") = Income(
        IncomeId.of(uuid(200 + number)).valueOrFail(),
        LocalDate.parse(date),
        number.toLong(),
        amount(pesos),
        IncomeSource.of("salary", null).valueOrFail(),
        null,
        1,
        recorded,
        recorded
    )

    private fun read(
        expenses: List<Expense> = emptyList(),
        incomes: List<Income> = emptyList(),
        categories: List<CategoryInMonth> = listOf(limited(1, 100_000)),
        month: YearMonth = october,
        day: FinancialDay = today
    ) = ResumenPolicy.snapshot(
        month,
        day,
        3,
        41,
        ResumenPolicy.monthsEndingIn(month).map { selected ->
            ResumenMonthInput(selected, categories, expenses)
        },
        incomes
    )

    private fun ResumenSnapshot.remaining() = (budget.overall as OverallBudget.Limited)
        .budget.remaining

    @Test
    fun theIncomeCountIsTheMonthsRecordedIncomesUpToToday() {
        val snapshot = read(
            incomes = listOf(
                income(1, 1_600_000, "2026-10-01"),
                income(2, 250_000, "2026-10-09"),
                // Dated after today (9 Oct): not counted, as the total leaves it out.
                income(3, 150_000, "2026-10-20"),
                income(4, 100_000, "2026-09-30")
            )
        ).valueOrFail()

        assertEquals(2, snapshot.incomeCount)
        assertEquals(Money.cop(1_850_000), snapshot.income)
    }

    @Test
    fun i01DistinguishesRecordedNetFlowRemainingAndUnassignedBudget() {
        val snapshot = read(listOf(expense(1, 30_000)), listOf(income(1, 150_000))).valueOrFail()
        assertEquals(Money.cop(150_000), snapshot.income)
        assertEquals(Money.cop(120_000), snapshot.netFlow)
        assertEquals(Money.cop(70_000), snapshot.remaining())
        assertEquals(Money.cop(100_000), snapshot.allocated)
        assertEquals(Money.cop(50_000), snapshot.unallocated)
        assertEquals(3L, snapshot.budget.generation)
        assertEquals(41L, snapshot.budget.revision)
        assertEquals(today.date, snapshot.budget.asOf)
    }

    @Test
    fun g01AndG02CorrectionsChangeTheGraphAndNetFlowButNotAllocation() {
        val initial = listOf(
            expense(1, 10_000),
            expense(2, 15_000, "2026-10-02"),
            expense(3, 5_000, "2026-10-03")
        )
        val incomes = listOf(income(1, 200_000))
        val original = read(initial, incomes).valueOrFail()
        val corrected = read(
            listOf(initial[0], expense(2, 12_000, "2026-10-02"), initial[2]),
            incomes
        ).valueOrFail()
        assertEquals(
            listOf(10_000L, 25_000L, 30_000L),
            original.daily.take(3).map {
                it.cumulative.minorUnits
            }
        )
        assertEquals(
            listOf(10_000L, 22_000L, 27_000L),
            corrected.daily.take(3).map {
                it.cumulative.minorUnits
            }
        )
        assertEquals(Money.cop(170_000), original.netFlow)
        assertEquals(Money.cop(173_000), corrected.netFlow)
        assertEquals(Money.cop(73_000), corrected.remaining())
        assertEquals(Money.cop(100_000), corrected.unallocated)
        assertEquals(original.unallocated, corrected.unallocated)
        assertEquals(Money.cop(27_000), corrected.budget.categories.single().spent)
        assertEquals(Money.cop(27_000), corrected.history.last().spent)
    }

    @Test
    fun actualVerdictNeverUsesTheProjectedOverspending() {
        // The pace projects more than the limit; neither Resumen nor Recibo judges on it.
        val snapshot = read(listOf(expense(1, 50_000))).valueOrFail()
        val current = (snapshot.budget.overall as OverallBudget.Limited).budget.standing
            as BudgetStanding.Current
        assertTrue(current.forecast.rounded.minorUnits > 100_000)
        assertEquals(Verdict.OnTrack, current.verdict)
        assertEquals(PastVerdict.WithinLimit, snapshot.actualVerdict)
        assertEquals(
            PastVerdict.Over,
            read(listOf(expense(1, 100_001))).valueOrFail().actualVerdict
        )
        assertEquals(
            PastVerdict.WithinLimit,
            read(listOf(expense(1, 100_000))).valueOrFail().actualVerdict
        )
    }

    @Test
    fun negativeFlowAndAllocationAreNotClampedAndUnlimitedIsNotAZeroBudget() {
        val negative = read(listOf(expense(1, 80_000)), listOf(income(1, 50_000))).valueOrFail()
        assertEquals(Money.cop(-30_000), negative.netFlow)
        assertEquals(Money.cop(-50_000), negative.unallocated)
        val unlimited = read(
            listOf(expense(1, 80_000)),
            listOf(income(1, 50_000)),
            listOf(category(1, BudgetLimit.Unlimited))
        ).valueOrFail()
        assertEquals(OverallBudget.NoLimit, unlimited.budget.overall)
        assertEquals(Money.cop(0), unlimited.allocated)
        assertEquals(Money.cop(50_000), unlimited.unallocated)
        assertNull(unlimited.actualVerdict)
        val zero = read(categories = listOf(limited(1, 0))).valueOrFail()
        assertEquals(PastVerdict.WithinLimit, zero.actualVerdict)
        assertEquals(Money.cop(0), zero.history.last().limit)
    }

    @Test
    fun theCutoffExcludesFutureRowsAndOtherMonthsWithoutInventingFutureActuals() {
        val snapshot = read(
            listOf(expense(1, 10_000), expense(2, 20_000, "2026-10-10")),
            listOf(
                income(1, 50_000),
                income(2, 70_000, "2026-10-10"),
                income(3, 80_000, "2026-09-01")
            )
        ).valueOrFail()
        assertEquals(Money.cop(10_000), snapshot.budget.spent)
        assertEquals(Money.cop(50_000), snapshot.income)
        assertEquals(1, snapshot.budget.futureDatedExpenses)
        assertEquals(1, snapshot.futureDatedIncomes)
        assertEquals(9, snapshot.daily.size)
        assertEquals(LocalDate.of(2026, 10, 9), snapshot.daily.last().date)
        assertEquals(Money.cop(0), snapshot.daily.last().spent)
        assertEquals(Money.cop(10_000), snapshot.daily.last().cumulative)
    }

    @Test
    fun historicalMonthsUseTheirOwnLimitsAndCompleteShortMonthSeries() {
        val february = YearMonth.of(2024, 2)
        val months = ResumenPolicy.monthsEndingIn(february).map { month ->
            ResumenMonthInput(
                month,
                listOf(limited(1, if (month == february) 100_000 else 90_000)),
                listOf(expense(1, 10_000, "2024-02-29"), expense(2, 20_000, "2024-01-31"))
            )
        }
        val snapshot = ResumenPolicy.snapshot(
            february,
            today,
            3,
            41,
            months,
            emptyList()
        ).valueOrFail()
        assertEquals(29, snapshot.daily.size)
        assertEquals(Money.cop(10_000), snapshot.daily.last().cumulative)
        assertEquals(12, snapshot.history.size)
        assertEquals(Money.cop(90_000), snapshot.history[snapshot.history.lastIndex - 1].limit)
        assertEquals(Money.cop(100_000), snapshot.history.last().limit)
        assertNull(snapshot.history.last().forecast)
        assertNull((snapshot.budget.overall as OverallBudget.Limited).allowance)
    }

    @Test
    fun emptyHistoryHasNoRecordedMonthsAndLargeExactValuesSurviveSelection() {
        val empty = read(categories = emptyList()).valueOrFail()
        assertEquals(Money.cop(0), empty.income)
        assertEquals(Money.cop(0), empty.netFlow)
        assertEquals(0, empty.history.sumOf { it.expenseCount })
        val large = read(
            listOf(expense(1, 999_999_999_999), expense(2, 999_999_999_999)),
            categories = listOf(category(1, BudgetLimit.Unlimited))
        ).valueOrFail()
        assertEquals(Money.cop(1_999_999_999_998), large.daily.first().spent)
        assertEquals(Money.cop(1_999_999_999_998), large.history.last().spent)
        assertEquals(Money.cop(-1_999_999_999_998), large.netFlow)
    }

    @Test
    fun overflowAndFutureMonthsAreTypedFailuresNotApproximateTotals() {
        assertEquals(
            SnapshotFailure.Unrepresentable,
            read(categories = listOf(limited(1, Long.MAX_VALUE), limited(2, 1))).errorOrFail()
        )
        assertEquals(
            SnapshotFailure.FutureMonth(october),
            read(month = YearMonth.of(2026, 11)).errorOrFail()
        )
    }

    @Test
    fun theBoundedHistoryDoesNotAskForUnstorableNegativeYears() {
        assertEquals(listOf(YearMonth.of(0, 1)), ResumenPolicy.monthsEndingIn(YearMonth.of(0, 1)))
    }
}
