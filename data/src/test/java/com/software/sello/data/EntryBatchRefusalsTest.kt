package com.software.sello.data

import com.software.sello.data.repository.expenseLineRefusals
import com.software.sello.data.repository.incomeLineRefusals
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ExpenseLine
import com.software.sello.domain.port.IncomeLine
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.RefusedLine
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which lines of a receipt are refused, and why: the date first, then the category, then
 * the total of the receipt's own month. Both kinds of receipt check that total.
 */
class EntryBatchRefusalsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).value()

    private val mercado = CategoryId.of(id(1)).value()
    private val salud = CategoryId.of(id(2)).value()
    private val borrada = CategoryId.of(id(3)).value()

    private val january10 = LocalDate.parse("2027-01-10")
    private val march5 = LocalDate.parse("2027-03-05")

    /** January holds 9.223.372.036.854.774.807, 1.000 below the largest Long; March nothing. */
    private val nearlyFull = listOf(Money.cop(9_223_372_036_854_774_807))
    private val asked = mutableListOf<YearMonth>()
    private val recordedIn: suspend (YearMonth) -> Outcome<List<Money>, StorageFailure> = {
        asked += it
        Outcome.Success(if (it == YearMonth.of(2027, 1)) nearlyFull else emptyList())
    }

    private val judged = mutableListOf<CategoryId>()
    private val category: suspend (CategoryId) -> Outcome<LineRefusal?, StorageFailure> = {
        judged += it
        Outcome.Success(
            when (it) {
                salud -> LineRefusal.CategoryArchived
                borrada -> LineRefusal.CategoryMissing
                else -> null
            }
        )
    }

    private fun expense(category: CategoryId, amount: Long) =
        ExpenseLine(category, pesos(amount), null)

    private fun income(amount: Long) = IncomeLine(IncomeSource.Salary, pesos(amount), null)

    @Test
    fun aReceiptOfExpensesIsHeldToTheTotalOfItsOwnMonthNotToday() = runTest {
        // 774.807 + 600 = 775.407; + 400 = 775.807, the largest Long; + 1 does not fit.
        val lines = listOf(expense(mercado, 600), expense(mercado, 400), expense(mercado, 1))

        val inJanuary = expenseLineRefusals(january10, march5, lines, category, recordedIn)
        val inMarch = expenseLineRefusals(march5, march5, lines, category, recordedIn)

        assertEquals(listOf(RefusedLine(2, LineRefusal.MonthTotalFull)), inJanuary.value())
        assertEquals(emptyList<RefusedLine>(), inMarch.value())
        assertEquals(listOf(YearMonth.of(2027, 1), YearMonth.of(2027, 3)), asked)
    }

    @Test
    fun aReceiptOfIncomesIsHeldToTheTotalOfItsOwnMonthNotToday() = runTest {
        val lines = listOf(income(600), income(400), income(1))

        val inJanuary = incomeLineRefusals(january10, march5, lines, recordedIn)
        val inMarch = incomeLineRefusals(march5, march5, lines, recordedIn)

        assertEquals(listOf(RefusedLine(2, LineRefusal.MonthTotalFull)), inJanuary.value())
        assertEquals(emptyList<RefusedLine>(), inMarch.value())
        assertEquals(listOf(YearMonth.of(2027, 1), YearMonth.of(2027, 3)), asked)
    }

    @Test
    fun aLineRefusedForItsCategoryKeepsThatReasonWhenTheMonthIsFullToo() = runTest {
        // 774.807 + 1.001 = 775.808, one past the largest Long: every line overflows.
        val lines = listOf(
            expense(salud, 1_001),
            expense(mercado, 1),
            expense(borrada, 5_000),
            expense(salud, 2)
        )

        val refused = expenseLineRefusals(january10, march5, lines, category, recordedIn)

        assertEquals(
            listOf(
                RefusedLine(0, LineRefusal.CategoryArchived),
                RefusedLine(1, LineRefusal.MonthTotalFull),
                RefusedLine(2, LineRefusal.CategoryMissing),
                RefusedLine(3, LineRefusal.CategoryArchived)
            ),
            refused.value()
        )
        // Salud is judged once for its two lines.
        assertEquals(listOf(salud, mercado, borrada), judged)
    }

    @Test
    fun aDateNoLineCanHaveRefusesEveryLineAndReadsNothing() = runTest {
        val tomorrow = march5.plusDays(1)

        val expenses = expenseLineRefusals(
            tomorrow,
            march5,
            listOf(expense(salud, 1), expense(mercado, 2)),
            category,
            recordedIn
        )
        val incomes = incomeLineRefusals(tomorrow, march5, listOf(income(1)), recordedIn)

        val future = LineRefusal.FutureDate(march5)
        assertEquals(listOf(RefusedLine(0, future), RefusedLine(1, future)), expenses.value())
        assertEquals(listOf(RefusedLine(0, future)), incomes.value())
        assertEquals(emptyList<CategoryId>(), judged)
        assertEquals(emptyList<YearMonth>(), asked)
    }

    @Test
    fun aMonthThatCannotBeReadFailsTheReceiptInsteadOfCountingAsEmpty() = runTest {
        val damaged = StorageFailure.Integrity("expense", id(100), "amount_minor")
        val unreadable: suspend (YearMonth) -> Outcome<List<Money>, StorageFailure> = {
            Outcome.Failure(damaged)
        }

        assertEquals(
            Outcome.Failure(damaged),
            expenseLineRefusals(
                january10,
                march5,
                listOf(expense(mercado, 1)),
                category,
                unreadable
            )
        )
        assertEquals(
            Outcome.Failure(damaged),
            incomeLineRefusals(january10, march5, listOf(income(1)), unreadable)
        )
    }
}
