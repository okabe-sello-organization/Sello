package com.software.sello.domain.policy

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryBatchPolicyTest {
    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun recorded(vararg pesos: Long) = ExactTotal.of(Currency.COP, pesos.map(::cop))

    @Test
    fun aReceiptHoldsFromOneToThirtyLines() {
        assertFalse(EntryBatchPolicy.holds(0))
        assertTrue(EntryBatchPolicy.holds(1))
        assertTrue(EntryBatchPolicy.holds(30))
        assertFalse(EntryBatchPolicy.holds(31))
        assertFalse(EntryBatchPolicy.holds(-1))
    }

    @Test
    fun linesAnOrdinaryMonthCanHoldAreNeverRefused() {
        // 30 lines of the largest amount a record can have: 29.999.999.999.970 in all.
        val largest = List(30) { cop(999_999_999_999) }

        assertEquals(
            emptyList<Int>(),
            EntryBatchPolicy.overflowing(recorded(1_250_000, 80_000), largest)
        )
    }

    @Test
    fun aTotalOfExactlyTheLargestAmountFitsAndOnePesoMoreDoesNot() {
        // The month holds 9.223.372.036.854.774.807, 1.000 below the largest Long.
        val month = recorded(9_223_372_036_854_774_807)

        // 774.807 + 600 = 775.407; + 400 = 775.807, the largest Long; + 1 does not fit.
        assertEquals(
            listOf(2),
            EntryBatchPolicy.overflowing(month, listOf(cop(600), cop(400), cop(1)))
        )
    }

    @Test
    fun everyLineAfterTheFirstThatDoesNotFitIsRefusedToo() {
        val month = recorded(9_223_372_036_854_774_807)

        // 774.807 + 1.001 = 775.808, one past the largest Long, so line 0 is refused, and
        // every later line adds to that total.
        assertEquals(
            listOf(0, 1, 2),
            EntryBatchPolicy.overflowing(month, listOf(cop(1_001), cop(1), cop(5_000)))
        )
    }

    @Test
    fun aMonthWhoseTotalAlreadyCannotBeHeldRefusesEveryLine() {
        // 2 × 4.611.686.018.427.387.904 = 9.223.372.036.854.775.808, one past the largest Long.
        val month = recorded(4_611_686_018_427_387_904, 4_611_686_018_427_387_904)

        assertEquals(listOf(0, 1), EntryBatchPolicy.overflowing(month, listOf(cop(1), cop(2))))
    }
}
