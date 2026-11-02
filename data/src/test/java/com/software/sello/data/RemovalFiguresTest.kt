package com.software.sello.data

import com.software.sello.data.repository.RemovalFigures
import com.software.sello.data.repository.removalFigures
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a category's expenses add up to before it is deleted (ADR 0024). Literals by hand. */
class RemovalFiguresTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val today = LocalDate.parse("2026-10-09")
    private val at = Instant.parse("2026-10-09T15:04:05.678Z")

    private fun expense(date: String, pesos: Long) = Expense(
        id = ExpenseId.of("00000000-0000-4000-8000-000000000101").value(),
        categoryId = CategoryId.of("00000000-0000-4000-8000-000000000100").value(),
        date = LocalDate.parse(date),
        sequence = 1,
        amount = TransactionAmount.of(Money.cop(pesos)).value(),
        note = null,
        version = 1,
        createdAt = at,
        updatedAt = at
    )

    @Test
    fun theTotalCountsEveryExpenseAndTheMonthOnlyWhatIsDatedByToday() {
        // 20.000 + 15.000 + 25.000 + 5.000 = 65.000; October by the 9th: 15.000 + 25.000.
        val figures = removalFigures(
            listOf(
                expense("2026-10-05", 25_000),
                expense("2026-08-14", 20_000),
                expense("2026-10-20", 5_000),
                expense("2026-10-03", 15_000)
            ),
            today
        )

        assertEquals(
            RemovalFigures(
                count = 4,
                total = Money.cop(65_000),
                firstMonth = YearMonth.of(2026, 8),
                lastMonth = YearMonth.of(2026, 10),
                spentThisMonth = Money.cop(40_000)
            ),
            figures
        )
    }

    @Test
    fun noExpensesAreZeroWithNoMonths() {
        assertEquals(
            RemovalFigures(0, Money.cop(0), null, null, Money.cop(0)),
            removalFigures(emptyList(), today)
        )
    }

    @Test
    fun aTotalThatDoesNotFitIsNoTotalAndIsNeverClipped() {
        val largest = expense("2026-09-01", 999_999_999_999)
        fun many(count: Int) = object : AbstractList<Expense>() {
            override val size = count

            override fun get(index: Int) = largest
        }

        // 9.223.372 × 999.999.999.999 = 9.223.371.999.990.776.628 fits; one more passes
        // 9.223.372.036.854.775.807, the largest Money.
        assertEquals(
            Money.cop(9_223_371_999_990_776_628),
            removalFigures(many(9_223_372), today)?.total
        )
        assertNull(removalFigures(many(9_223_373), today))
    }
}
