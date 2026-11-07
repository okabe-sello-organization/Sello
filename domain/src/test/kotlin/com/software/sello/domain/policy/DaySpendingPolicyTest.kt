package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MoneyError
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DaySpendingPolicyTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun aDayAddsUpEveryExpenseOfThatDay() {
        val days = DaySpendingPolicy.byDay(
            listOf(
                today to Money.cop(18_000),
                today to Money.cop(2_950),
                monday to Money.cop(1_100_000),
                today to Money.cop(8_000),
                monday to Money.cop(96_400)
            )
        ).valueOrFail()

        // 18.000 + 2.950 + 8.000 = 28.950; 1.100.000 + 96.400 = 1.196.400, worked by hand.
        assertEquals(mapOf(today to Money.cop(28_950), monday to Money.cop(1_196_400)), days)
    }

    @Test
    fun aDayWithNoExpenseHasNoSum() {
        // Only expenses come in: a day with only a 2.500.000 income has nothing to add up.
        val days = DaySpendingPolicy.byDay(listOf(today to Money.cop(15_000))).valueOrFail()

        assertFalse(LocalDate.of(2026, 10, 3) in days)
        assertEquals(mapOf(today to Money.cop(15_000)), days)
    }

    @Test
    fun aDaySumThatDoesNotFitMoneyIsATypedFailureNeverWrapped() {
        val failure = DaySpendingPolicy.byDay(
            listOf(
                today to Money.cop(Long.MAX_VALUE),
                today to Money.cop(1),
                monday to Money.cop(5_000)
            )
        ).errorOrFail()

        assertEquals(MoneyError.Overflow, failure)
    }

    @Test
    fun anotherCurrencyIsAMismatchNotAddedIn() {
        val failure = DaySpendingPolicy.byDay(
            listOf(today to Money.cop(5_000), today to Money(500, Currency.USD))
        ).errorOrFail()

        assertEquals(MoneyError.CurrencyMismatch(Currency.COP, Currency.USD), failure)
    }
}
