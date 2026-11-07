package com.software.sello.presentation.appearance

import com.software.sello.R
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
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

    @Test
    fun eachInkIsDrawnInItsOwnPaletteAndTuyaInItsHue() {
        for (phone in phones) {
            assertEquals(SelloInk.Cobalto, InkPreference.Cobalto.themeInk(phone))
            assertEquals(SelloInk.Violeta, InkPreference.Violeta.themeInk(phone))
            assertEquals(HueInk(22), InkPreference.Tuya(InkHue.of(22)!!).themeInk(phone))
            assertEquals(HueInk(0), InkPreference.Tuya(InkHue.of(0)!!).themeInk(phone))
            assertEquals(HueInk(359), InkPreference.Tuya(InkHue.of(359)!!).themeInk(phone))
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
        for (ink in listOf(InkPreference.Violeta, InkPreference.Tuya(InkHue.of(22)!!))) {
            for (phone in phones) assertEquals(ink, ink.shown(phone))
        }
    }

    @Test
    fun tuTintaStartsOnTuyasHueOrElseTheHueOfTheInkInUse() {
        assertEquals(267, AppearancePreference(ink = InkPreference.Cobalto).startingHue(blue))
        assertEquals(286, AppearancePreference(ink = InkPreference.Violeta).startingHue(blue))
        val green = InkHue.of(147)!!
        assertEquals(147, AppearancePreference(ink = InkPreference.Tuya(green)).startingHue(blue))
        // Kept while another ink is in use.
        assertEquals(
            147,
            AppearancePreference(ink = InkPreference.Violeta, tuyaHue = green).startingHue(blue)
        )
        assertEquals(
            147,
            AppearancePreference(ink = InkPreference.Sistema, tuyaHue = green).startingHue(blue)
        )
        // Sistema with no Tuya yet: the phone's hue, or Cobalto's where it has none to give.
        val sistema = AppearancePreference(ink = InkPreference.Sistema)
        assertEquals(140, sistema.startingHue(blue))
        assertEquals(267, sistema.startingHue(PhoneColor.Grey))
        assertEquals(267, sistema.startingHue(PhoneColor.None))
    }
}
