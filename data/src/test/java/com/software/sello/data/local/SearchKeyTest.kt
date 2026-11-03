package com.software.sello.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** Search ignores case and every accent (SELLO-165); the note's own text is never changed. */
class SearchKeyTest {
    @Test
    fun capitalsAndAccentsFoldToPlainLowerCase() {
        assertEquals("name area", SearchKey.of("ÑAME ÁREA"))
        assertEquals("panaderia", SearchKey.of("Panadería"))
        assertEquals("pinguino", SearchKey.of("PINGÜINO"))
        assertEquals("creme brulee", SearchKey.of("Crème brûlée"))
    }

    @Test
    fun combiningAccentsFoldLikeTheLettersThatCarryThem() {
        assertEquals(SearchKey.of("Café"), SearchKey.of("Café"))
        assertEquals("cafe", SearchKey.of("Café"))
    }

    @Test
    fun noNoteIsTheEmptyKeyThatEveryEmptyQueryFinds() {
        assertEquals("", SearchKey.of(null))
        assertEquals("", SearchKey.of(""))
    }

    @Test
    fun digitsAndSignsStayAsTheyAre() {
        assertEquals("zeta 100% #2", SearchKey.of("Zeta 100% #2"))
    }
}
