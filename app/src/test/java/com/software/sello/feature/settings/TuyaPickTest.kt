package com.software.sello.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuyaPickTest {
    @Test
    fun thePlacesOfTheBarAndTheSquareGiveTheirColours() {
        assertEquals(0xFF0000, TuyaPick(0f, 1f, 1f).rgb())
        assertEquals(0xFFFF00, TuyaPick(60f, 1f, 1f).rgb())
        assertEquals(0x00FF00, TuyaPick(120f, 1f, 1f).rgb())
        assertEquals(0x0000FF, TuyaPick(240f, 1f, 1f).rgb())
        // Half way from red to yellow: green at half, 127.5 rounded up.
        assertEquals(0xFF8000, TuyaPick(30f, 1f, 1f).rgb())
        // The left edge is white at the top and grey half way down, whatever the hue.
        assertEquals(0xFFFFFF, TuyaPick(200f, 0f, 1f).rgb())
        assertEquals(0x808080, TuyaPick(200f, 0f, 0.5f).rgb())
        // The bottom is black.
        assertEquals(0x000000, TuyaPick(200f, 1f, 0f).rgb())
    }

    @Test
    fun aColourStandsAtItsOwnHueAndAGreyKeepsTheBarsHue() {
        assertEquals(TuyaPick(0f, 1f, 1f), TuyaPick.of(0xFF0000, 200f))
        assertEquals(TuyaPick(120f, 1f, 1f), TuyaPick.of(0x00FF00, 200f))
        assertEquals(TuyaPick(240f, 1f, 1f), TuyaPick.of(0x0000FF, 200f))
        assertEquals(TuyaPick(300f, 1f, 1f), TuyaPick.of(0xFF00FF, 200f))
        assertEquals(TuyaPick(200f, 0f, 1f), TuyaPick.of(0xFFFFFF, 200f))
        assertEquals(TuyaPick(45f, 0f, 0f), TuyaPick.of(0x000000, 45f))
        assertEquals(TuyaPick(45f, 0f, 128 / 255f), TuyaPick.of(0x808080, 45f))
        // #C72842: red 199 is the largest; (40 - 66) / 159 of a sixth below red is 350.19°.
        val pick = TuyaPick.of(0xC72842, 0f)
        assertEquals(350.19f, pick.hue, 0.01f)
        assertEquals(159 / 199f, pick.saturation, 1e-6f)
        assertEquals(199 / 255f, pick.value, 1e-6f)
    }

    @Test
    fun aColourGoesBackToItselfFromWhereItStands() {
        for (rgb in listOf(0xC72842, 0x1F3BE0, 0xF4C430, 0x5F6368, 0x2E7D32, 0x010203)) {
            assertEquals("%06X".format(rgb), rgb, TuyaPick.of(rgb, 0f).rgb())
        }
    }

    @Test
    fun onlyPlacesOnTheBarAndTheSquareAreValid() {
        assertTrue(TuyaPick(0f, 0f, 0f).isValid)
        assertTrue(TuyaPick(359.9f, 1f, 1f).isValid)
        assertFalse(TuyaPick(360f, 1f, 1f).isValid)
        assertFalse(TuyaPick(-0.1f, 1f, 1f).isValid)
        assertFalse(TuyaPick(0f, 1.01f, 1f).isValid)
        assertFalse(TuyaPick(0f, 1f, -0.01f).isValid)
        assertFalse(TuyaPick(Float.NaN, 1f, 1f).isValid)
        assertFalse(TuyaPick(0f, Float.NaN, 1f).isValid)
    }

    @Test
    fun aCodeIsSixHexDigitsWithOrWithoutTheHashInAnyCase() {
        assertEquals(0xC72842, colourOfCode("#C72842"))
        assertEquals(0xC72842, colourOfCode("C72842"))
        assertEquals(0xC72842, colourOfCode("#c72842"))
        assertEquals(0xC7284A, colourOfCode("c7284A"))
        assertEquals(0x000000, colourOfCode("#000000"))
        assertEquals(0xFFFFFF, colourOfCode("ffffff"))
        for (text in listOf(
            "", "#", "#C7284", "C7284", "#C728421", "C728421", "C7284G", " #C72842", "#C72842 ",
            "##C72842", "0xC72842", "#-12345", "+C7284", "#C7 842"
        )) {
            assertNull("'$text'", colourOfCode(text))
        }
    }

    @Test
    fun aColoursCodeIsHashAndSixCapitals() {
        assertEquals("#C72842", codeOf(0xC72842))
        assertEquals("#00000A", codeOf(0x00000A))
        assertEquals("#FFFFFF", codeOf(0xFFFFFF))
    }
}
