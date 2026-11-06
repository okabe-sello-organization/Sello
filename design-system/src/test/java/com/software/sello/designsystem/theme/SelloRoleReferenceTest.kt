package com.software.sello.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The roles that are another role by design stay that role in every palette (SELLO-190),
 * so a scheme that changes one moves the other with it.
 */
@RunWith(Parameterized::class)
class SelloRoleReferenceTest(private val ink: SelloInk, private val dark: Boolean) {
    private val c = selloColors(ink, dark)

    @Test
    fun lightBandsAreTheRolesTheyShow() {
        if (dark) return
        assertEquals(c.brand, c.band)
        assertEquals(c.onBrand, c.onBand)
        assertEquals(c.semantic.gain, c.goalBand)
        assertEquals(c.ink, c.investmentBand)
    }

    @Test
    fun investmentTextIsThePalettesInk() {
        val darkText = if (dark) selloColors(ink, isDark = false).ink else c.ink
        val written = (1..c.investments.size).map { c.investmentInk(it).on }
        val expected = if (dark) {
            // Slate, the light fill, takes the family's light ink.
            listOf(rgb(0xFFFFFF), darkText, rgb(0xFFFFFF), rgb(0x2A0A1C), rgb(0xFFFFFF))
        } else {
            listOf(darkText, rgb(0xFFFFFF), darkText, rgb(0xFFFFFF), darkText)
        }
        assertEquals(expected, written)
    }

    @Test
    fun theScrimIsBlackAsThePlatformsWindowDim() {
        // DialogSlip's dim is the platform's black; RepeatSheet's must match it.
        assertEquals(Color.Black, c.scrim)
        assertEquals(c.scrim, c.toMaterialColorScheme().scrim)
    }

    @Test
    fun violetaSharesCobaltosOwnColoursByReference() {
        if (ink == SelloInk.Cobalto) return
        val base = selloColors(SelloInk.Cobalto, dark)
        assertEquals(base.onBand, c.onBand)
        assertEquals(base.goalBand, c.goalBand)
        assertEquals(base.onPastBar, c.onPastBar)
        assertEquals(base.onPastBarSoft, c.onPastBarSoft)
        if (!dark) {
            assertEquals(base.onBrand, c.onBrand)
            assertEquals(base.paper, c.paper)
        }
    }

    @Test
    fun theSwatchIsTheInksLightBandInEitherTheme() {
        assertEquals(selloColors(ink, isDark = false).band, ink.swatch)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} dark={1}")
        fun palettes(): List<Array<Any>> =
            SelloInk.entries.flatMap { ink -> listOf(false, true).map { arrayOf(ink, it) } }
    }
}
