package com.software.sello.presentation.appearance

import androidx.compose.ui.graphics.toArgb
import com.software.sello.R
import com.software.sello.designsystem.theme.ColourInk
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.platform.PhoneColor
import org.junit.Assert.assertEquals
import org.junit.Test

class InkChoicesTest {
    @Test
    fun everyHandMadeInkAndEveryPaletteIsInThePickerOnce() {
        assertEquals(
            listOf(InkPreference.Cobalto, InkPreference.Violeta),
            InkChoices.map { it.preference }
        )
        assertEquals(SelloInk.entries, InkChoices.map { it.ink })
        assertEquals(
            listOf(R.string.settings_ink_cobalto, R.string.settings_ink_violeta),
            InkChoices.map { it.label }
        )
    }

    private val blue = PhoneColor.Hue(140)
    private val phones = listOf(PhoneColor.None, PhoneColor.Grey, blue)

    private fun hue(degrees: Long) = InkPreference.Tuya(TuyaColour.Hue(InkHue.of(degrees)!!))
    private fun exact(rgb: Long) = InkPreference.Tuya(TuyaColour.Exact(InkColour.of(rgb)!!))

    @Test
    fun eachInkIsDrawnInItsOwnPaletteAndTuyaInItsColourOrHue() {
        for (phone in phones) {
            assertEquals(SelloInk.Cobalto, InkPreference.Cobalto.themeInk(phone))
            assertEquals(SelloInk.Violeta, InkPreference.Violeta.themeInk(phone))
            // A hue saved before E39 is drawn as E37 draws it, never as a colour.
            assertEquals(HueInk(22), hue(22).themeInk(phone))
            assertEquals(HueInk(0), hue(0).themeInk(phone))
            assertEquals(HueInk(359), hue(359).themeInk(phone))
            assertEquals(ColourInk(0xF2C200), exact(0xF2C200).themeInk(phone))
            assertEquals(ColourInk(0x000000), exact(0x000000).themeInk(phone))
            assertEquals(ColourInk(0xFFFFFF), exact(0xFFFFFF).themeInk(phone))
        }
    }

    @Test
    fun sistemaIsThePhonesHueAndCobaltoWhereThePhoneHasNone() {
        assertEquals(HueInk(140), InkPreference.Sistema.themeInk(blue))
        assertEquals(HueInk(0), InkPreference.Sistema.themeInk(PhoneColor.Hue(0)))
        assertEquals(HueInk(359), InkPreference.Sistema.themeInk(PhoneColor.Hue(359)))
        assertEquals(SelloInk.Cobalto, InkPreference.Sistema.themeInk(PhoneColor.None))
        assertEquals(SelloInk.Cobalto, InkPreference.Sistema.themeInk(PhoneColor.Grey))
    }

    @Test
    fun sistemaShowsAsCobaltoOnlyWhereThePhoneHasNoAccent() {
        assertEquals(InkPreference.Sistema, InkPreference.Sistema.shown(blue))
        // A grey accent: Sistema is still offered and stays chosen.
        assertEquals(InkPreference.Sistema, InkPreference.Sistema.shown(PhoneColor.Grey))
        // Android 11: not offered, so shown as the Cobalto it is drawn in.
        assertEquals(InkPreference.Cobalto, InkPreference.Sistema.shown(PhoneColor.None))
        for (ink in listOf(InkPreference.Violeta, hue(22), exact(0x808080))) {
            for (phone in phones) assertEquals(ink, ink.shown(phone))
        }
    }

    /** The 24-bit colour a hue's palette draws its light band in: E37's generator. */
    private fun bandOf(hue: Int) = HueInk(hue).swatch.toArgb() and 0xFFFFFF

    @Test
    fun tuTintaStartsOnTuyasColourElseItsHuesColourElseTheInkInUse() {
        // Cobalto's and Violeta's light bands.
        assertEquals(
            0x1F3BE0,
            AppearancePreference(ink = InkPreference.Cobalto).startingColour(blue)
        )
        assertEquals(
            0x5B2BD6,
            AppearancePreference(ink = InkPreference.Violeta).startingColour(blue)
        )
        // Tuya's kept colour, exactly, in use or kept while another ink is.
        val yellow = TuyaColour.Exact(InkColour.of(0xF4C430)!!)
        assertEquals(
            0xF4C430,
            AppearancePreference(ink = InkPreference.Tuya(yellow)).startingColour(blue)
        )
        assertEquals(
            0xF4C430,
            AppearancePreference(ink = InkPreference.Sistema, tuya = yellow).startingColour(blue)
        )
        // A hue saved before E39: the colour its palette draws, not converted.
        val green = TuyaColour.Hue(InkHue.of(147)!!)
        assertEquals(
            bandOf(147),
            AppearancePreference(ink = InkPreference.Tuya(green)).startingColour(blue)
        )
        assertEquals(
            bandOf(147),
            AppearancePreference(ink = InkPreference.Violeta, tuya = green).startingColour(blue)
        )
        // Sistema with no Tuya yet: the phone's colour, or Cobalto's where it has none to give.
        val sistema = AppearancePreference(ink = InkPreference.Sistema)
        assertEquals(bandOf(140), sistema.startingColour(blue))
        assertEquals(0x1F3BE0, sistema.startingColour(PhoneColor.Grey))
        assertEquals(0x1F3BE0, sistema.startingColour(PhoneColor.None))
    }

    @Test
    fun antesIsTheColourOfTheInkInUseNotTuyasKeptOne() {
        assertEquals(0x1F3BE0, InkPreference.Cobalto.colour(blue))
        assertEquals(0x5B2BD6, InkPreference.Violeta.colour(blue))
        assertEquals(bandOf(140), InkPreference.Sistema.colour(blue))
        assertEquals(0x1F3BE0, InkPreference.Sistema.colour(PhoneColor.None))
        assertEquals(0x000000, exact(0x000000).colour(blue))
        assertEquals(bandOf(22), hue(22).colour(blue))
    }
}
