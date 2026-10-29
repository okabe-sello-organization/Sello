package com.software.sello.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Colour rules that are not about one palette. Each palette's own: [SelloPaletteTest]. */
class SelloColorsTest {
    @Test
    fun semanticColoursMatchTheReference() {
        val light = selloColors(SelloInk.Cobalto, isDark = false).semantic
        assertEquals(rgb(0x0A6B4A), light.gain)
        assertEquals(rgb(0xCDEFE0), light.gainSoft)
        assertEquals(rgb(0xB81E2A), light.loss)
        assertEquals(rgb(0xFBD9DB), light.lossSoft)
        assertEquals(rgb(0x8A4A00), light.warn)
        assertEquals(rgb(0x6A3FC0), light.dividend)

        val dark = selloColors(SelloInk.Cobalto, isDark = true).semantic
        assertEquals(rgb(0x4FD6A0), dark.gain)
        assertEquals(rgb(0x0E4A35), dark.gainSoft)
        assertEquals(rgb(0xFF8A8A), dark.loss)
        assertEquals(rgb(0x5A171B), dark.lossSoft)
        assertEquals(rgb(0xFFB45C), dark.warn)
        assertEquals(rgb(0xB7A0F5), dark.dividend)
    }

    @Test
    fun contrastHelperAgreesWithPublishedRatios() {
        val light = selloColors(SelloInk.Cobalto, isDark = false)
        assertEquals(17.9, contrast(light.ink, light.paper), 0.05)
        assertEquals(7.6, contrast(light.onBrand, light.brand), 0.05)
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
    }
}

internal fun rgb(hex: Int): Color = Color(0xFF000000 or hex.toLong())

/** WCAG 2.x contrast ratio, written here independently of production code. */
internal fun contrast(a: Color, b: Color): Double {
    fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    fun luminance(c: Color) =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
