package com.software.sello.designsystem.theme

import com.software.sello.designsystem.component.bandTrough
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every registered palette (each ink × light/dark), checked once here so component tests
 * need only the base ink. A new ink adds its row to [reference] and nothing else.
 */
@RunWith(Parameterized::class)
class SelloPaletteTest(private val ink: SelloInk, private val dark: Boolean) {
    private val c = selloColors(ink, dark)

    @Test
    fun carriesTheReferenceBrandFamily() {
        val hex = reference[ink to dark]
        assertNotNull("$ink dark=$dark has no reference values", hex)
        val actual = listOf(
            c.brand, c.onBrand, c.brandSoft, c.onBrandSoft, c.desk, c.paper, c.paperAlt,
            c.paperDim, c.ink, c.inkSoft, c.outline, c.rule, c.band, c.pastBar
        )
        assertEquals(hex!!.map(::rgb), actual)
        assertEquals(ink, c.family)
        assertEquals(dark, c.isDark)
    }

    @Test
    fun changesBrandButNeverMeaning() {
        val base = selloColors(SelloInk.Cobalto, dark)
        if (ink != SelloInk.Cobalto) {
            assertNotEquals(base.brand, c.brand)
            assertNotEquals(base.band, c.band)
            assertNotEquals(base.desk, c.desk)
        }
        assertEquals(base.semantic, c.semantic)
        assertEquals(base.categories, c.categories)
    }

    @Test
    fun eachEntryBandStandsApart() {
        // Expense, goal and investment forms are told apart by their band at a glance.
        val bands = listOf(c.band, c.goalBand, c.investmentBand)
        assertEquals(3, bands.distinct().size)
        // In light they are the docks' own colours: Aportar's green, Anotar movimiento's ink.
        if (!dark) {
            assertEquals(c.semantic.gain, c.goalBand)
            assertEquals(c.ink, c.investmentBand)
        }
    }

    @Test
    fun twelveCategoryColours() {
        assertEquals(12, c.categories.distinct().size)
    }

    @Test
    fun everyReferencePairMeetsItsContrastNeed() {
        val s = c.semantic
        val text = 4.5
        val mark = 3.0
        val pairs = buildList {
            listOf(c.desk, c.paper, c.paperAlt, c.paperDim).forEach {
                add(Triple(c.ink, it, text))
            }
            listOf(c.desk, c.paper, c.paperAlt).forEach {
                add(Triple(c.inkSoft, it, text))
                add(Triple(c.brand, it, text))
            }
            add(Triple(c.onBrand, c.brand, text))
            add(Triple(c.onBrandSoft, c.brandSoft, text))
            add(Triple(c.onBand, c.band, text))
            // The entry forms' bands carry the same title, close and switch as the band.
            add(Triple(c.onBand, c.goalBand, text))
            add(Triple(c.onBand, c.investmentBand, text))
            add(Triple(c.band, c.onBand, text))
            add(Triple(c.goalBand, c.onBand, text))
            add(Triple(c.investmentBand, c.onBand, text))
            // The side of a band's switch not chosen: white on the lighter trough.
            listOf(c.band, c.goalBand, c.investmentBand).forEach {
                add(Triple(c.onBand, bandTrough(it, c.onBand), text))
            }
            listOf(c.desk, c.paper).forEach {
                add(Triple(s.gain, it, text))
                add(Triple(s.loss, it, text))
                add(Triple(s.warn, it, text))
                add(Triple(c.outline, it, mark))
            }
            add(Triple(c.pastBar, c.paper, mark))
            add(Triple(s.onGain, s.gain, text))
            add(Triple(s.onLoss, s.loss, text))
            // Stamps, slips and the past-month bar: each "on" colour on its own fill.
            add(Triple(c.onPastBar, c.pastBar, text))
            add(Triple(c.onPastBarSoft, c.pastBarSoft, text))
            add(Triple(s.onGainSoft, s.gainSoft, text))
            add(Triple(s.onLossSoft, s.lossSoft, text))
            add(Triple(s.gain, s.gainSoft, text))
            add(Triple(s.loss, s.lossSoft, text))
            add(Triple(s.warn, s.warnSoft, text))
            add(Triple(c.inkSoft, c.paperDim, text))
            add(Triple(c.brand, c.paperDim, text))
            listOf(c.brandSoft, c.pastBarSoft, s.gainSoft, s.lossSoft, s.warnSoft).forEach {
                add(Triple(c.ink, it, text))
            }
            c.categories.forEach { category ->
                listOf(c.desk, c.paper, c.paperAlt).forEach { add(Triple(category, it, mark)) }
            }
        }
        val failures = pairs.filter { (fg, bg, need) -> contrast(fg, bg) < need }
            .map { (fg, bg, need) -> "$ink dark=$dark: $fg on $bg needs $need" }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} dark={1}")
        fun palettes(): List<Array<Any>> =
            SelloInk.entries.flatMap { ink -> listOf(false, true).map { arrayOf(ink, it) } }

        /**
         * brand, onBrand, brandSoft, onBrandSoft, desk, paper, paperAlt, paperDim, ink,
         * inkSoft, outline, rule, band, pastBar: typed from the reference's foundations
         * table, not derived from the code.
         */
        private val reference = mapOf(
            (SelloInk.Cobalto to false) to listOf(
                0x1F3BE0, 0xFFFFFF, 0xDCE2FF, 0x0B1C7A, 0xD9E1F7, 0xFFFFFF, 0xEEF2FC,
                0xD3DCF3, 0x0E1630, 0x434E72, 0x6975A0, 0xB9C5E6, 0x1F3BE0, 0x566287
            ),
            (SelloInk.Cobalto to true) to listOf(
                0x8FA6FF, 0x06175C, 0x23368F, 0xDDE4FF, 0x090D1C, 0x151C36, 0x1D2646,
                0x2A3560, 0xEEF1FB, 0xA9B3D6, 0x8490BA, 0x2F3B66, 0x2238B8, 0xA7B2D2
            ),
            (SelloInk.Violeta to false) to listOf(
                0x5B2BD6, 0xFFFFFF, 0xE6DCFF, 0x2A0F78, 0xE2DAF6, 0xFFFFFF, 0xF3EEFC,
                0xDCD2F2, 0x150F2E, 0x4A4370, 0x75679F, 0xC4B7E6, 0x5B2BD6, 0x5F5887
            ),
            (SelloInk.Violeta to true) to listOf(
                0xB79CFF, 0x22085E, 0x3A1F8F, 0xE9DFFF, 0x0E0A1C, 0x1A1433, 0x231B45,
                0x30265C, 0xF1EEFB, 0xB3ABD6, 0x8E82B8, 0x372C66, 0x4420B0, 0xABA3CF
            )
        )
    }
}
