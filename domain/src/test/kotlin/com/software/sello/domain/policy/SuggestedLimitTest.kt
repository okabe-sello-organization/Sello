package com.software.sello.domain.policy

import com.software.sello.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedLimitTest {
    @Test
    fun anAmountTheFieldCanHoldIsSuggestedAsPlainPesos() {
        assertEquals("1", SuggestedLimit.of(Money.cop(1)))
        assertEquals("50000", SuggestedLimit.of(Money.cop(50_000)))
        assertEquals("1500000", SuggestedLimit.of(Money.cop(1_500_000)))
    }

    @Test
    fun theLargestTwelveDigitAmountIsKeptWholeAndOneMoreIsLeftOut() {
        assertEquals("999999999999", SuggestedLimit.of(Money.cop(999_999_999_999)))
        assertNull(SuggestedLimit.of(Money.cop(1_000_000_000_000)))
        assertNull(SuggestedLimit.of(Money.cop(Long.MAX_VALUE)))
    }

    @Test
    fun aNegativeAmountIsNeverALimit() {
        assertNull(SuggestedLimit.of(Money.cop(-1)))
    }

    @Test
    fun aHintIsTakenOnlyAsPlainPesosTheFieldCanHold() {
        listOf("0", "7", "50000", "999999999999").forEach {
            assertTrue(it, SuggestedLimit.accepts(it))
        }
        listOf(
            "", " ", " 5", "-5", "050000", "00", "50.000", "1e3", "5,5", "abc",
            "1000000000000", "99999999999999999999"
        ).forEach { assertFalse(it, SuggestedLimit.accepts(it)) }
    }
}
