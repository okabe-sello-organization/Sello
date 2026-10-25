package com.software.sello.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typing USD amounts and the rate with the pad's comma (IN32, IN34). */
class DecimalDraftTest {
    private fun type(keys: String, maxWhole: Int = 6): DecimalDraft.Change {
        var change = DecimalDraft.Change(DecimalDraft(maxWhole))
        for (key in keys) {
            change = if (key == ',') change.draft.comma() else change.draft.digit(key.digitToInt())
        }
        return change
    }

    @Test
    fun aRateIsTypedWithItsCentavos() {
        assertEquals(415_025L, type("4150,25").draft.hundredths())
        assertEquals(415_000L, type("4150").draft.hundredths())
        assertEquals(415_020L, type("4150,2").draft.hundredths())
    }

    @Test
    fun aThirdDecimalIsRefusedNotRounded() {
        val change = type("4150,255")
        assertTrue(change.limitReached)
        assertEquals(415_025L, change.draft.hundredths())
    }

    @Test
    fun theWholeDigitsStopAtTheirLimit() {
        val rate = type("1000000")
        assertTrue(rate.limitReached)
        assertEquals(10_000_000L, rate.draft.hundredths())
        // USD 9.999.999.999,99 is ten whole digits (IN34).
        assertEquals(999_999_999_999L, type("9999999999,99", maxWhole = 10).draft.hundredths())
    }

    @Test
    fun aCommaFirstReadsZeroAndOnlyOneIsKept() {
        assertEquals(DecimalDraft(6, "0", comma = true), type(",").draft)
        assertTrue(type("1,,").limitReached)
        assertEquals(50L, type(",5").draft.hundredths())
    }

    @Test
    fun leadingZerosAreNotKept() {
        assertEquals("0", type("00").draft.whole)
        assertEquals("7", type("07").draft.whole)
        assertEquals(70L, type("0,7").draft.hundredths())
    }

    @Test
    fun backspaceUndoesOneKeyAtATime() {
        var draft = type("12,5").draft
        draft = draft.backspace()
        assertEquals(DecimalDraft(6, "12", comma = true), draft)
        draft = draft.backspace()
        assertEquals(DecimalDraft(6, "12"), draft)
        draft = draft.backspace().backspace()
        assertNull(draft.hundredths())
    }

    @Test
    fun aSavedFigureComesBackAsItWasWritten() {
        assertEquals(DecimalDraft(6, "4150", comma = true, cents = "25"), DecimalDraft.of(415_025, 6))
        assertEquals(DecimalDraft(6, "4150", comma = true, cents = "2"), DecimalDraft.of(415_020, 6))
        assertEquals(DecimalDraft(6, "4000"), DecimalDraft.of(400_000, 6))
    }
}
