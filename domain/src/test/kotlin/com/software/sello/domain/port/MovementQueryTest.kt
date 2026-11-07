package com.software.sello.domain.port

import com.software.sello.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovementQueryTest {
    private fun amountOf(typed: String) = MovementQuery.of(typed)?.amount?.money

    @Test
    fun plainDigitsAndCorrectlyGroupedDotsAreAnAmount() {
        assertEquals(Money.cop(18_000), amountOf("18000"))
        assertEquals(Money.cop(18_000), amountOf("18.000"))
        assertEquals(Money.cop(1), amountOf("1"))
        assertEquals(Money.cop(1_250_000), amountOf("1.250.000"))
        assertEquals(Money.cop(999_999_999_999), amountOf("999.999.999.999"))
        // Spaces around the query are not part of it.
        assertEquals(Money.cop(18_000), amountOf("  18.000 "))
    }

    @Test
    fun anythingElseIsSearchedOnlyAsWords() {
        listOf(
            "1.80.00",
            "18.00",
            "1.8000",
            "18,000",
            "18.000,50",
            "1e3",
            "-18000",
            "+18000",
            "$18.000",
            "018000",
            "18 000",
            "١٨"
        ).forEach { typed ->
            val query = MovementQuery.of(typed)
            assertEquals(typed, typed.trim(), query?.words)
            assertNull(typed, query?.amount)
        }
    }

    @Test
    fun zeroAndAmountsPastTheRecordCapFindNoAmount() {
        // They parse as COP, but no expense or income can hold them.
        assertNull(amountOf("0"))
        assertNull(amountOf("1.000.000.000.000"))
        assertNull(amountOf("99999999999999999999"))
        assertEquals("0", MovementQuery.of("0")?.words)
    }

    @Test
    fun wordsAreTheQueryWithoutSurroundingSpaces() {
        val query = MovementQuery.of("  Rappí cena ")
        assertEquals("Rappí cena", query?.words)
        assertNull(query?.amount)
    }

    @Test
    fun nothingIsSearchedForBlankOrTooLongText() {
        assertNull(MovementQuery.of(""))
        assertNull(MovementQuery.of("   "))
        assertEquals(60, MovementQuery.of("a".repeat(60))?.words?.length)
        assertNull(MovementQuery.of("a".repeat(61)))
    }
}
