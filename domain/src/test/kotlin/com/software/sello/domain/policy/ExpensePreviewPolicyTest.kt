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
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.pesos
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpensePreviewPolicyTest {
    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = CategoryId.of(uuid(number)).valueOrFail()

    private fun category(number: Int, state: MonthBudgetState) = CategoryInMonth(
        Category(
            id(number),
            CategoryName.of("Categoría $number").valueOrFail(),
            IconKey.of("home").valueOrFail(),
            archived = false,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        state
    )

    private fun limited(number: Int, limit: Long) = category(
        number,
        MonthBudgetState.Limited(BudgetLimit.finite(Money.cop(limit)).valueOrFail(), false)
    )

    private fun unlimited(number: Int) =
        category(number, MonthBudgetState.Limited(BudgetLimit.Unlimited, false))

    private fun expense(number: Int, category: Int, amount: Long) = Expense(
        ExpenseId.of(uuid(number)).valueOrFail(),
        id(category),
        LocalDate.of(2026, 10, 5),
        number.toLong(),
        pesos(amount),
        note = null,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun snapshot(
        categories: List<CategoryInMonth>,
        expenses: List<Expense>
    ): MonthlySnapshot = MonthlyBudgetPolicy.snapshot(
        YearMonth.of(2026, 10),
        FinancialDay(LocalDate.of(2026, 10, 22), ZoneId.of("America/Bogota")),
        generation = 1,
        revision = 1,
        categories = categories,
        expenses = expenses
    ).valueOrFail()

    private fun remaining(amount: Long) = PreviewFigure.Remaining(Money.cop(amount))

    private fun spent(amount: Long) = PreviewFigure.Spent(Money.cop(amount))

    private fun over(by: Long) = PreviewFigure.Over(Money.cop(by))

    @Test
    fun theMonthAndTheCategoryShowWhatWouldBeLeftAfterTheExpense() {
        // The reference's screen: 937.200 left in the month, 287.600 in Alimentación,
        // and an expense of 48.700.
        val month = snapshot(
            listOf(limited(1, 900_000), limited(2, 650_000)),
            listOf(expense(100, 1, 612_400), expense(101, 2, 400))
        )

        val preview = ExpensePreviewPolicy.preview(month, pesos(48_700), id(1)).valueOrFail()

        assertEquals(ExpensePreview(remaining(888_500), remaining(238_900)), preview)
    }

    @Test
    fun goingOverALimitIsShownAsHowFarOverNotHidden() {
        // "Te pasas por 12.300 en Café": 8.800 left, an expense of 21.100.
        val month = snapshot(
            listOf(limited(1, 50_000), limited(2, 500_000)),
            listOf(expense(100, 1, 41_200))
        )

        val preview = ExpensePreviewPolicy.preview(month, pesos(21_100), id(1)).valueOrFail()

        assertEquals(over(12_300), preview.category)
        assertEquals(remaining(487_700), preview.month)
    }

    @Test
    fun aCategoryWithoutALimitShowsWhatWouldHaveBeenSpentAndStillCountsInTheMonth() {
        val month = snapshot(
            listOf(limited(1, 100_000), unlimited(2)),
            listOf(expense(100, 2, 20_000))
        )

        val preview = ExpensePreviewPolicy.preview(month, pesos(5_000), id(2)).valueOrFail()

        assertEquals(ExpensePreview(remaining(75_000), spent(25_000)), preview)
    }

    @Test
    fun withNoLimitAtAllTheMonthShowsWhatWouldHaveBeenSpent() {
        val month = snapshot(listOf(unlimited(1)), listOf(expense(100, 1, 50_000)))

        val preview = ExpensePreviewPolicy.preview(month, pesos(10_000), id(1)).valueOrFail()

        assertEquals(ExpensePreview(spent(60_000), spent(60_000)), preview)
    }

    @Test
    fun usingExactlyWhatIsLeftLeavesZeroAndOnePesoMoreIsOver() {
        val month = snapshot(listOf(limited(1, 100_000)), listOf(expense(100, 1, 60_000)))

        val exact = ExpensePreviewPolicy.preview(month, pesos(40_000), id(1)).valueOrFail()
        val onePesoMore = ExpensePreviewPolicy.preview(month, pesos(40_001), id(1)).valueOrFail()

        assertEquals(ExpensePreview(remaining(0), remaining(0)), exact)
        assertEquals(ExpensePreview(over(1), over(1)), onePesoMore)
    }

    @Test
    fun aMonthAlreadyOverGoesFurtherOver() {
        val month = snapshot(listOf(limited(1, 100_000)), listOf(expense(100, 1, 110_000)))

        val preview = ExpensePreviewPolicy.preview(month, pesos(5_000), id(1)).valueOrFail()

        assertEquals(ExpensePreview(over(15_000), over(15_000)), preview)
    }

    @Test
    fun beforeACategoryIsChosenOnlyTheMonthIsPreviewed() {
        val month = snapshot(listOf(limited(1, 100_000)), emptyList())

        val none = ExpensePreviewPolicy.preview(month, pesos(10_000), null).valueOrFail()
        val unknown = ExpensePreviewPolicy.preview(month, pesos(10_000), id(9)).valueOrFail()

        assertEquals(ExpensePreview(remaining(90_000), null), none)
        assertNull(unknown.category)
    }

    @Test
    fun theLargestExpenseAgainstTheLargestLimitIsStillExact() {
        val month = snapshot(listOf(limited(1, Long.MAX_VALUE)), emptyList())

        val preview =
            ExpensePreviewPolicy.preview(month, pesos(999_999_999_999), id(1)).valueOrFail()

        assertEquals(remaining(Long.MAX_VALUE - 999_999_999_999), preview.month)
    }

    @Test
    fun aFigureThatDoesNotFitIsAFailureNotAWrappedNumber() {
        // Nearly as far over as an amount can express. One peso more lands on the one
        // value that has no positive counterpart; two pesos more cannot be held at all.
        val base = snapshot(listOf(limited(1, 0)), emptyList())
        val limited = base.overall as OverallBudget.Limited
        val atTheEdge = limited.budget.copy(remaining = Money.cop(Long.MIN_VALUE + 1))
        val month = base.copy(overall = limited.copy(budget = atTheEdge))

        val onePeso = ExpensePreviewPolicy.preview(month, pesos(1), null)
        val twoPesos = ExpensePreviewPolicy.preview(month, pesos(2), null)

        assertEquals(SnapshotFailure.Unrepresentable, onePeso.errorOrFail())
        assertEquals(SnapshotFailure.Unrepresentable, twoPesos.errorOrFail())
    }
}
