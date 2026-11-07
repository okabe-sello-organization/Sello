package com.software.sello.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A search compares any part of a category's name by the name's own key (SELLO-193). */
class CategoryNameKeyTest {
    private fun key(name: String) = (CategoryName.of(name) as Outcome.Success).value.uniquenessKey

    @Test
    fun aQueryFoldsAsTheNameItLooksFor() {
        assertEquals("rappi", CategoryName.keyOf("RAPPÍ"))
        assertEquals("rappi", CategoryName.keyOf("rappí"))
        assertEquals("alimentacion", CategoryName.keyOf("Alimentación"))
        assertEquals("cafe con", CategoryName.keyOf("Café   con"))
    }

    @Test
    fun anyPartOfANameIsInsideItsKey() {
        assertTrue(key("Alimentación").contains(CategoryName.keyOf("ACIÓN")))
        assertTrue(key("Café con leche").contains(CategoryName.keyOf("cafe  CON")))
    }
}
