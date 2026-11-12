package com.software.sello.designsystem.theme

import com.software.sello.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The palette any hue gives (SELLO-201): Cobalto's, turned, as readable at every hue. */
class HuePaletteTest {
    @Test
    fun aColoursHueIsItsNearestWholeOklchDegree() {
        // OKLCH hues worked out apart from this code: sRGB red 29.23°, green 142.50°, blue
        // 264.05°, Cobalto's brand 266.64°.
        assertEquals(29, hueOf(0xFFFF0000.toInt()))
        assertEquals(142, hueOf(0xFF00FF00.toInt()))
        assertEquals(264, hueOf(0xFF0000FF.toInt()))
        assertEquals(COBALTO_HUE, hueOf(0xFF1F3BE0.toInt()))
        // 359.98° is nearest to 0°, round the circle: never 360.
        assertEquals(0, hueOf(0xFFC64075.toInt()))
    }

    @Test
    fun aGreyHasNoHue() {
        for (grey in listOf(0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF7F7F80)) {
            assertNull(grey.toString(16), hueOf(grey.toInt()))
        }
    }

    @Test
    fun cobaltosHueGivesCobalto() {
        val light = hueColors(COBALTO_HUE, isDark = false)
        val dark = hueColors(COBALTO_HUE, isDark = true)
        // Every turned role, typed from the foundations table, not derived from the code.
        assertEquals(
            listOf(
                0x1F3BE0, 0xFFFFFF, 0xDCE2FF, 0x0B1C7A, 0xD9E1F7, 0xFFFFFF, 0xEEF2FC, 0xD3DCF3,
                0x0E1630, 0x434E72, 0x6975A0, 0xB9C5E6, 0x1F3BE0, 0xFFFFFF, 0x0E1630, 0x566287,
                0xFFFFFF, 0xE1E7F6, 0x17203C
            ).map(::rgb),
            turnedRoles(light)
        )
        assertEquals(
            listOf(
                0x8FA6FF, 0x06175C, 0x23368F, 0xDDE4FF, 0x090D1C, 0x151C36, 0x1D2646, 0x2A3560,
                0xEEF1FB, 0xA9B3D6, 0x8490BA, 0x2F3B66, 0x2238B8, 0xFFFFFF, 0x2C3454, 0xA7B2D2,
                0x17203C, 0x2A3560, 0xE1E7F6
            ).map(::rgb),
            turnedRoles(dark)
        )
        assertEquals(selloColors(SelloInk.Cobalto, isDark = false), light)
        assertEquals(selloColors(SelloInk.Cobalto, isDark = true), dark)
    }

    @Test
    fun aGreenHueMatchesThePrototype() {
        // gen.js's algorithm turned 120° from Cobalto. The port turns from 267°, not the
        // mock's 266.64°, so gen.js at 147 differs by one 8-bit step in some roles.
        assertEquals(
            listOf(
                0x00621E, 0xFFFFFF, 0xCDEAD7, 0x00320B, 0xD2E6D5, 0xFFFFFF, 0xEBF4EC, 0xCDE1CF,
                0x021C08, 0x33573C, 0x558061, 0xAFCDB3, 0x00621E, 0xFFFFFF, 0x021C08, 0x476B4F,
                0xFFFFFF, 0xDDEADF, 0x092711
            ).map(::rgb),
            turnedRoles(hueColors(147, isDark = false))
        )
        assertEquals(
            listOf(
                0x5FBD7C, 0x002605, 0x004A1B, 0xD1EBD8, 0x031106, 0x06230F, 0x0B2E16, 0x0F4020,
                0xEAF4EC, 0x9ABCA3, 0x729B7C, 0x174625, 0x00551E, 0xFFFFFF, 0x1A3C25, 0x9CBAA1,
                0x092711, 0x0F4020, 0xDDEADF
            ).map(::rgb),
            turnedRoles(hueColors(147, isDark = true))
        )
    }

    @Test
    fun theInkTurnsButMeaningsStay() {
        for (dark in listOf(false, true)) {
            val base = selloColors(SelloInk.Cobalto, dark)
            val c = hueColors(147, dark)
            assertNotEquals(base.brand, c.brand)
            assertNotEquals(base.desk, c.desk)
            assertNotEquals(base.band, c.band)
            assertNotEquals(base.ink, c.ink)
            assertEquals(base.semantic, c.semantic)
            assertEquals(base.goalBand, c.goalBand)
            assertEquals(base.scrim, c.scrim)
            assertEquals(base.investments.map { it.fill }, c.investments.map { it.fill })
            assertEquals(SelloInk.Cobalto, c.family)
            assertEquals(dark, c.isDark)
        }
    }

    @Test
    fun everyHueIsAsReadableAsCobalto() {
        val failures = (0..359).flatMap { hue ->
            listOf(false, true).flatMap { dark ->
                val c = hueColors(hue, dark)
                val bands = if (entryBands(c).distinct().size == 3) {
                    emptyList()
                } else {
                    listOf("entry bands not distinct")
                }
                (contrastFailures(c) + investmentNeighbourFailures(c) + bands)
                    .map { "hue $hue: $it" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun redAndGreenEndWhereTheMockSays() {
        assertEquals(HueMeaning.Red, hueMeaning(0))
        assertEquals(HueMeaning.Red, hueMeaning(49))
        assertNull(hueMeaning(50))
        assertNull(hueMeaning(121))
        assertEquals(HueMeaning.Green, hueMeaning(122))
        assertEquals(HueMeaning.Green, hueMeaning(195))
        assertNull(hueMeaning(196))
        assertNull(hueMeaning(COBALTO_HUE))
        assertNull(hueMeaning(359))
    }

    @Test
    fun eachHueIsNamedAsInTheMock() {
        val edges = listOf(
            0 to R.string.sello_hue_rosa, 11 to R.string.sello_hue_rosa,
            12 to R.string.sello_hue_rojo, 44 to R.string.sello_hue_rojo,
            45 to R.string.sello_hue_naranja, 69 to R.string.sello_hue_naranja,
            70 to R.string.sello_hue_ambar, 91 to R.string.sello_hue_ambar,
            92 to R.string.sello_hue_amarillo, 111 to R.string.sello_hue_amarillo,
            112 to R.string.sello_hue_lima, 134 to R.string.sello_hue_lima,
            135 to R.string.sello_hue_verde, 167 to R.string.sello_hue_verde,
            168 to R.string.sello_hue_turquesa, 199 to R.string.sello_hue_turquesa,
            200 to R.string.sello_hue_petroleo, 224 to R.string.sello_hue_petroleo,
            225 to R.string.sello_hue_azul, 249 to R.string.sello_hue_azul,
            250 to R.string.sello_hue_anil, 281 to R.string.sello_hue_anil,
            282 to R.string.sello_hue_violeta, 304 to R.string.sello_hue_violeta,
            305 to R.string.sello_hue_morado, 329 to R.string.sello_hue_morado,
            330 to R.string.sello_hue_fucsia, 351 to R.string.sello_hue_fucsia,
            352 to R.string.sello_hue_rosa, 359 to R.string.sello_hue_rosa
        )
        edges.forEach { (hue, name) -> assertEquals("hue $hue", name, hueName(hue)) }
    }

    @Test
    fun aHueOutsideTheCircleIsRejectedNotWrapped() {
        listOf(-1, 360).forEach { hue ->
            assertThrows(IllegalArgumentException::class.java) { hueColors(hue, isDark = false) }
            assertThrows(IllegalArgumentException::class.java) { hueName(hue) }
            assertThrows(IllegalArgumentException::class.java) { hueMeaning(hue) }
            assertThrows(IllegalArgumentException::class.java) { HueInk(hue) }
        }
    }

    @Test
    fun aHueInkDrawsTheGeneratedPaletteAndItsSwatchIsItsLightBand() {
        // Hue 147's light band, typed as aGreenHueMatchesThePrototype has it.
        assertEquals(rgb(0x00621E), HueInk(147).swatch)
        for (hue in 0..359) {
            val ink = HueInk(hue)
            assertEquals("hue $hue", hueColors(hue, isDark = false).band, ink.swatch)
        }
        for (dark in listOf(false, true)) {
            assertEquals(hueColors(147, dark), selloColors(HueInk(147), dark))
        }
    }

    /**
     * brand, onBrand, brandSoft, onBrandSoft, desk, paper, paperAlt, paperDim, ink, inkSoft,
     * outline, rule, band, onBand, investmentBand, pastBar, onPastBar, pastBarSoft,
     * onPastBarSoft.
     */
    private fun turnedRoles(c: SelloColors) = listOf(
        c.brand, c.onBrand, c.brandSoft, c.onBrandSoft, c.desk, c.paper, c.paperAlt, c.paperDim,
        c.ink, c.inkSoft, c.outline, c.rule, c.band, c.onBand, c.investmentBand, c.pastBar,
        c.onPastBar, c.pastBarSoft, c.onPastBarSoft
    )
}
