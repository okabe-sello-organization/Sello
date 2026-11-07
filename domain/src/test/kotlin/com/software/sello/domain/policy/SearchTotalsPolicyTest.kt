package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTotalsPolicyTest {
    private fun day(text: String) = LocalDate.parse(text)

    /** The mock's «rappi» search: nine expenses over three months. */
    private val rappi = listOf(
        day("2026-10-03") to Money.cop(20_600),
        day("2026-10-01") to Money.cop(15_000),
        day("2026-10-01") to Money.cop(25_600),
        day("2026-09-27") to Money.cop(22_100),
        day("2026-09-19") to Money.cop(41_200),
        day("2026-09-10") to Money.cop(17_000),
        day("2026-09-02") to Money.cop(18_000),
        day("2026-08-12") to Money.cop(27_900),
        day("2026-08-05") to Money.cop(27_400)
    )

    @Test
    fun eachMonthCountsAndAddsUpItsMatchingExpensesNewestFirst() {
        val totals = SearchTotalsPolicy.of(rappi, emptyList()).valueOrFail()

        // Worked by hand: 20.600 + 15.000 + 25.600 = 61.200;
        // 22.100 + 41.200 + 17.000 + 18.000 = 98.300; 27.900 + 27.400 = 55.300.
        assertEquals(
            listOf(
                MonthMatches(YearMonth.of(2026, 10), 3, Money.cop(61_200)),
                MonthMatches(YearMonth.of(2026, 9), 4, Money.cop(98_300)),
                MonthMatches(YearMonth.of(2026, 8), 2, Money.cop(55_300))
            ),
            totals.months
        )
        // 61.200 + 98.300 + 55.300 = 214.800, nine expenses.
        assertEquals(Matches(9, Money.cop(214_800)), totals.expenses)
        assertNull(totals.incomes)
    }

    @Test
    fun incomesAreCountedInTheirMonthButNeverAddedToItsSpending() {
        val totals = SearchTotalsPolicy.of(
            listOf(day("2026-10-01") to Money.cop(15_000)),
            listOf(
                day("2026-10-03") to Money.cop(2_500_000),
                day("2026-07-03") to Money.cop(650_000)
            )
        ).valueOrFail()

        assertEquals(
            listOf(
                MonthMatches(YearMonth.of(2026, 10), 2, Money.cop(15_000)),
                // A month where only an income matched has no spending sum.
                MonthMatches(YearMonth.of(2026, 7), 1, null)
            ),
            totals.months
        )
        assertEquals(Matches(1, Money.cop(15_000)), totals.expenses)
        // 2.500.000 + 650.000 = 3.150.000.
        assertEquals(Matches(2, Money.cop(3_150_000)), totals.incomes)
    }

    @Test
    fun theSameMonthOfAnotherYearIsItsOwnGroup() {
        val totals = SearchTotalsPolicy.of(
            listOf(
                day("2025-10-20") to Money.cop(18_000),
                day("2026-10-20") to Money.cop(18_000),
                day("2026-10-04") to Money.cop(18_000)
            ),
            emptyList()
        ).valueOrFail()

        assertEquals(
            listOf(
                MonthMatches(YearMonth.of(2026, 10), 2, Money.cop(36_000)),
                MonthMatches(YearMonth.of(2025, 10), 1, Money.cop(18_000))
            ),
            totals.months
        )
        // 18.000 × 3 = 54.000.
        assertEquals(Matches(3, Money.cop(54_000)), totals.expenses)
    }

    @Test
    fun nothingFoundIsNoMonthsAndNoMatches() {
        val totals = SearchTotalsPolicy.of(emptyList(), emptyList()).valueOrFail()

        assertEquals(SearchTotals(emptyList(), null, null), totals)
    }

    @Test
    fun aMonthTooLargeToAddUpExactlyFailsTheWholeAnswer() {
        val error = SearchTotalsPolicy.of(
            listOf(
                day("2026-10-01") to Money.cop(Long.MAX_VALUE),
                day("2026-10-02") to Money.cop(1)
            ),
            emptyList()
        ).errorOrFail()

        assertEquals(MoneyError.Overflow, error)
    }

    @Test
    fun matchesAddingUpPastAnExactAmountAcrossMonthsFailToo() {
        // Each month fits; their sum across months does not.
        val error = SearchTotalsPolicy.of(
            emptyList(),
            listOf(
                day("2026-10-01") to Money.cop(Long.MAX_VALUE),
                day("2026-09-01") to Money.cop(1)
            )
        ).errorOrFail()

        assertEquals(MoneyError.Overflow, error)
    }

    @Test
    fun mixedCurrenciesAreNeverAddedUp() {
        val error = SearchTotalsPolicy.of(
            listOf(
                day("2026-10-01") to Money.cop(15_000),
                day("2026-10-02") to Money(500, Currency.USD)
            ),
            emptyList()
        ).errorOrFail()

        assertEquals(MoneyError.CurrencyMismatch(Currency.COP, Currency.USD), error)
    }
}
