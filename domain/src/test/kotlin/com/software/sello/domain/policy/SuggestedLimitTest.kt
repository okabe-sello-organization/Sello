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

    @Test
    fun theKeypadAndTheSuggestionShareOneDefinitionOfWhatFits() {
        // The keypad builds on digits with no leading zero, up to twelve of them.
        assertTrue(LimitKeypad.fits("1"))
        assertTrue(LimitKeypad.fits("999999999999"))
        listOf("", "0", "05", "1000000000000", "1.000", "-1", "a").forEach {
            assertFalse(it, LimitKeypad.fits(it))
        }
        // Whatever is suggested (zero apart) is something the keypad fits.
        listOf(1L, 50_000L, 999_999_999_999L).forEach {
            assertTrue(LimitKeypad.fits(SuggestedLimit.of(Money.cop(it))!!))
        }
        // Zero is a real limit the keypad types as 0, and the suggestion keeps it.
        assertEquals("0", SuggestedLimit.of(Money.cop(0)))
    }
}
