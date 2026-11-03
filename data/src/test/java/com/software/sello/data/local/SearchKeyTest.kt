package com.software.sello.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/** Search ignores case and every accent (SELLO-165); the note's own text is never changed. */
class SearchKeyTest {
    @Test
    fun capitalsAndAccentsFoldToPlainLowerCase() {
        assertEquals("name area", SearchKey.of("\u00d1AME \u00c1REA"))
        assertEquals("panaderia", SearchKey.of("Panader\u00eda"))
        assertEquals("pinguino", SearchKey.of("PING\u00dcINO"))
        assertEquals("creme brulee", SearchKey.of("Cr\u00e8me br\u00fbl\u00e9e"))
    }

    @Test
    fun combiningAccentsFoldLikeTheLettersThatCarryThem() {
        assertEquals(SearchKey.of("Caf\u00e9"), SearchKey.of("Cafe\u0301"))
        assertEquals("cafe", SearchKey.of("Cafe\u0301"))
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
