package com.software.sello.designsystem.theme

import com.software.sello.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The palette any exact colour gives (SELLO-214): the colour as it is, the rest readable. */
class ColourPaletteTest {
    @Test
    fun theLightBrandAndBandAreTheColourExactlyWithWhiteOnThem() {
        val c = colourColors(0xC72842, isDark = false)
        assertEquals(rgb(0xC72842), c.brand)
        assertEquals(rgb(0xC72842), c.band)
        assertEquals(rgb(0xFFFFFF), c.onBrand)
        assertEquals(rgb(0xFFFFFF), c.onBand)
    }

    @Test
    fun aRedMatchesThePrototype() {
        // The mock's script (palette, tint) run on #C72842, typed here, not derived from
        // this code.
        assertEquals(
            listOf(
                0xC72842, 0xFFFFFF, 0xFEDAD9, 0x5B0014, 0xF6DAD9, 0xFFFFFF, 0xFCEFEE, 0xF2D4D4,
                0x2C0C0E, 0x6E4243, 0x9C6768, 0xE4BABA, 0xC72842, 0xFFFFFF, 0x2C0C0E, 0x835556,
                0xFFFFFF, 0xF5E2E2, 0x381618
            ).map(::rgb),
            roles(colourColors(0xC72842, isDark = false))
        )
        assertEquals(
            listOf(
                0xFF888E, 0x47000E, 0x7E0421, 0xFEDCDC, 0x1A0809, 0x321314, 0x411A1D, 0x5A2629,
                0xFBEEEE, 0xD3A8A8, 0xB68283, 0x602C2E, 0xB00030, 0xFFFFFF, 0x50292B, 0xD0A7A7,
                0x381618, 0x5A2629, 0xF5E2E2
            ).map(::rgb),
            roles(colourColors(0xC72842, isDark = true))
        )
    }

    @Test
    fun aGreyGivesGreyRoles() {
        // The mock's script run on #5F6368: every role a grey, the brand the colour.
        assertEquals(
            listOf(
                0x5F6368, 0xFFFFFF, 0xE2E4E6, 0x292E33, 0xE0E1E3, 0xFFFFFF, 0xF1F2F3, 0xDBDCDE,
                0x17181A, 0x4E5052, 0x75777A, 0xC4C6C8, 0x5F6368, 0xFFFFFF, 0x17181A, 0x626466,
                0xFFFFFF, 0xE6E7E8, 0x202224
            ).map(::rgb),
            roles(colourColors(0x5F6368, isDark = false))
        )
        assertEquals(
            listOf(
                0xADB1B7, 0x1F2227, 0x3E4248, 0xE4E5E7, 0x0D0E0F, 0x1D1E20, 0x27282B, 0x36393C,
                0xF1F1F2, 0xB3B4B7, 0x909295, 0x3C3E41, 0x4E5257, 0xFFFFFF, 0x353639, 0xB1B3B5,
                0x202224, 0x36393C, 0xE6E7E8
            ).map(::rgb),
            roles(colourColors(0x5F6368, isDark = true))
        )
    }

    @Test
    fun meaningsNeverFollowTheColour() {
        for (dark in listOf(false, true)) {
            val c = colourColors(0xC72842, dark)
            // Cobalto's gain, loss and warn, typed from the foundations table.
            val (gain, loss, warn) = if (dark) {
                Triple(0x4FD6A0, 0xFF8A8A, 0xFFB45C)
            } else {
                Triple(0x0A6B4A, 0xB81E2A, 0x8A4A00)
            }
            assertEquals(rgb(gain), c.semantic.gain)
            assertEquals(rgb(loss), c.semantic.loss)
            assertEquals(rgb(warn), c.semantic.warn)
            val base = selloColors(SelloInk.Cobalto, dark)
            assertEquals(base.semantic, c.semantic)
            assertEquals(base.goalBand, c.goalBand)
            assertEquals(base.scrim, c.scrim)
            assertEquals(base.investments.map { it.fill }, c.investments.map { it.fill })
            assertEquals(SelloInk.Cobalto, c.family)
            assertEquals(dark, c.isDark)
        }
    }

    @Test
    fun everyColourKeepsTheRestReadable() {
        // 16 steps a channel, 0 to 255, across the whole sRGB cube.
        val levels = (0..255 step 17).toList()
        val failures = levels.flatMap { r ->
            levels.flatMap { g ->
                levels.flatMap { b ->
                    val colour = (r shl 16) or (g shl 8) or b
                    listOf(false, true).flatMap { dark ->
                        val c = colourColors(colour, dark)
                        val darkBrandText = if (dark && contrast(c.onBrand, c.brand) < 4.5) {
                            listOf("dark onBrand on brand needs 4.5")
                        } else {
                            emptyList()
                        }
                        (contrastFailures(c, exemptBrandAndBand = true) + darkBrandText)
                            .map { "#%06X: %s".format(colour, it) }
                    }
                }
            }
        }
        assertTrue(failures.take(50).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun closenessToTheMeaningsIsAsTheMockSays() {
        listOf(0xC62828, 0xE53935).forEach {
            assertEquals("#%06X".format(it), HueMeaning.Red, colourMeaning(it))
        }
        listOf(0x2E7D32, 0x4CAF50, 0x13704C).forEach {
            assertEquals("#%06X".format(it), HueMeaning.Green, colourMeaning(it))
        }
        listOf(0xE06A1F, 0xFF6FAE, 0xF4C430, 0x5F6368, 0x1F3BE0).forEach {
            assertNull("#%06X".format(it), colourMeaning(it))
        }
    }

    @Test
    fun aGreyIsNeverNearAMeaning() {
        for (level in 0..255) {
            val grey = (level shl 16) or (level shl 8) or level
            assertNull("#%06X".format(grey), colourMeaning(grey))
        }
    }

    @Test
    fun everyColourHasASpanishName() {
        assertEquals(R.string.sello_hue_rojo, colourName(0xC72842))
        assertEquals(R.string.sello_hue_anil, colourName(0x1F3BE0))
        assertEquals(R.string.sello_hue_ambar, colourName(0xF4C430))
        assertEquals(R.string.sello_hue_verde, colourName(0x2E7D32))
        assertEquals(R.string.sello_colour_gris, colourName(0x5F6368))
        assertEquals(R.string.sello_colour_gris, colourName(0x808080))
        assertEquals(R.string.sello_colour_negro, colourName(0x000000))
        assertEquals(R.string.sello_colour_blanco, colourName(0xFFFFFF))
    }

    @Test
    fun aValueOutsideTwentyFourBitsIsRejectedNotMasked() {
        listOf(-1, 0x1000000, 0xFFC72842.toInt()).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { colourColors(value, false) }
            assertThrows(IllegalArgumentException::class.java) { colourMeaning(value) }
            assertThrows(IllegalArgumentException::class.java) { colourName(value) }
        }
    }

    /**
     * brand, onBrand, brandSoft, onBrandSoft, desk, paper, paperAlt, paperDim, ink, inkSoft,
     * outline, rule, band, onBand, investmentBand, pastBar, onPastBar, pastBarSoft,
     * onPastBarSoft.
     */
    private fun roles(c: SelloColors) = listOf(
        c.brand, c.onBrand, c.brandSoft, c.onBrandSoft, c.desk, c.paper, c.paperAlt, c.paperDim,
        c.ink, c.inkSoft, c.outline, c.rule, c.band, c.onBand, c.investmentBand, c.pastBar,
        c.onPastBar, c.pastBarSoft, c.onPastBarSoft
    )
}
