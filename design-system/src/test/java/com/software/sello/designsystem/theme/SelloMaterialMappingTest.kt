package com.software.sello.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelloMaterialMappingTest {
    private val schemes = SelloInk.entries.flatMap { ink ->
        listOf(false, true).map { dark -> selloColors(ink, dark) }
    }

    @Test
    fun materialSlotsFollowTheReferenceTable() {
        for (c in schemes) {
            val m = c.toMaterialColorScheme()
            assertEquals(c.brand, m.primary)
            assertEquals(c.onBrand, m.onPrimary)
            assertEquals(c.brandSoft, m.primaryContainer)
            assertEquals(c.onBrandSoft, m.onPrimaryContainer)
            assertEquals(c.desk, m.background)
            assertEquals(c.paper, m.surface)
            assertEquals(c.paperAlt, m.surfaceContainer)
            assertEquals(c.paperDim, m.surfaceContainerHigh)
            assertEquals(c.ink, m.onSurface)
            assertEquals(c.inkSoft, m.onSurfaceVariant)
            assertEquals(c.outline, m.outline)
            assertEquals(c.rule, m.outlineVariant)
            assertEquals(c.pastBar, m.secondary)
            assertEquals(c.semantic.gain, m.tertiary)
            assertEquals(c.semantic.gainSoft, m.tertiaryContainer)
            assertEquals(c.semantic.loss, m.error)
            assertEquals(c.semantic.lossSoft, m.errorContainer)
            assertEquals(c.scrim, m.scrim)
        }
    }

    /**
     * Reads every colour slot the pinned Material version exposes, so a slot added by
     * a later Material release cannot quietly bring a baseline colour with it.
     */
    @Test
    fun noMaterialSlotFallsBackToAColourOutsideSello() {
        for (c in schemes) {
            val s = c.semantic
            val sello = setOf(
                c.brand, c.onBrand, c.brandSoft, c.onBrandSoft, c.desk, c.paper, c.paperAlt,
                c.paperDim, c.ink, c.inkSoft, c.outline, c.rule, c.band, c.onBand, c.pastBar,
                c.onPastBar, c.pastBarSoft, c.onPastBarSoft, s.gain, s.onGain, s.gainSoft,
                s.onGainSoft, s.loss, s.onLoss, s.lossSoft, s.onLossSoft, c.scrim
            )
            val slots = materialSlots(c.toMaterialColorScheme())
            assertTrue("expected Material colour slots, found ${slots.size}", slots.size >= 36)
            val foreign = slots.filterValues { it !in sello }
            assertTrue("${c.family} dark=${c.isDark}: $foreign", foreign.isEmpty())
        }
    }

    private fun materialSlots(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.declaredMethods
            .filter {
                it.name.startsWith("get") && it.parameterCount == 0 &&
                    it.returnType == Long::class.javaPrimitiveType
            }
            .associate {
                val value = it.invoke(scheme) as Long
                it.name.substringBefore('-') to Color(value.toULong())
            }
}
